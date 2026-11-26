package planner.win;

import java.lang.foreign.AddressLayout;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JFrame;

import planner.DesktopHost;

/**
 * Windows desktop attachment using the JDK foreign function API (JDK 22+, no extra library).
 *
 * Method: the board stays a normal top-level window, but its OWNER is set to the desktop
 * window (Progman), and it is kept at the bottom of the window stack:
 *   1. FindWindow("Progman") - the desktop window.
 *   2. Find the board's own HWND by giving the frame a temporary unique title.
 *   3. SetWindowLongPtr(board, GWLP_HWNDPARENT, progman) - owned windows get no taskbar button.
 *   4. WS_EX_NOACTIVATE, so clicking the board never raises it above other windows.
 *   5. SetWindowPos(board, HWND_BOTTOM) regularly, in case something else raised it.
 *
 * What was tried first (Windows 11, build 26200):
 *   - SetParent to the WorkerW behind the icons (the usual wallpaper method): on 24H2+ the
 *     WorkerW is a child of Progman and SHELLDLL_DefView lies on top of it, so all clicks
 *     went to the icon layer.
 *   - SetParent to Progman above the icon layer: Windows reported the window as visible
 *     and on top, but the board was not drawn on screen (Java renders by itself and this
 *     does not show inside Explorer's desktop window).
 * Keeping the board a real top-level window avoids both problems.
 */
public final class Win32Desktop implements DesktopHost {

    private static final AddressLayout A = ValueLayout.ADDRESS;
    private static final ValueLayout.OfInt I = ValueLayout.JAVA_INT;
    private static final ValueLayout.OfLong J = ValueLayout.JAVA_LONG;

    private static final int GWLP_HWNDPARENT = -8;
    private static final int GW_OWNER = 4;
    private static final long HWND_BOTTOM = 1;
    private static final long HWND_TOP = 0;
    private static final int GWL_EXSTYLE = -20;
    private static final long WS_EX_NOACTIVATE = 0x08000000L;
    private static final int SWP_NOSIZE = 0x0001;
    private static final int SWP_NOMOVE = 0x0002;
    private static final int SWP_NOACTIVATE = 0x0010;

    private static final int LVM_GETITEMCOUNT = 0x1004;
    private static final int LVM_GETITEMPOSITION = 0x1010;
    private static final int LVM_GETITEMSPACING = 0x1033;
    private static final int PROCESS_VM_OPERATION = 0x0008;
    private static final int PROCESS_VM_READ = 0x0010;
    private static final int MEM_COMMIT_RESERVE = 0x3000;
    private static final int MEM_RELEASE = 0x8000;
    private static final int PAGE_READWRITE = 0x04;

    private final Arena arena = Arena.ofAuto();
    private final MethodHandle findWindowEx;
    private final MethodHandle isWindowVisible;
    private final MethodHandle sendMessage;
    private final MethodHandle getWindowThreadProcessId;
    private final MethodHandle mapWindowPoints;
    private final MethodHandle openProcess;
    private final MethodHandle virtualAllocEx;
    private final MethodHandle virtualFreeEx;
    private final MethodHandle readProcessMemory;
    private final MethodHandle closeHandle;
    private final MethodHandle findWindow;
    private final MethodHandle setWindowLongPtr;
    private final MethodHandle getWindowLongPtr;
    private final MethodHandle getWindow;
    private final MethodHandle setWindowPos;
    private final MethodHandle isWindow;
    private final MethodHandle setForegroundWindow;
    /** True while a memo on the board is being typed in (WS_EX_NOACTIVATE is off then). */
    private volatile boolean typing;

    private MemorySegment board = MemorySegment.NULL;
    private MemorySegment progman = MemorySegment.NULL;

    public Win32Desktop() {
        Linker linker = Linker.nativeLinker();
        SymbolLookup user32 = SymbolLookup.libraryLookup("user32", Arena.global());
        findWindow = handle(linker, user32, "FindWindowW", FunctionDescriptor.of(A, A, A));
        setWindowLongPtr = handle(linker, user32, "SetWindowLongPtrW", FunctionDescriptor.of(J, A, I, J));
        getWindowLongPtr = handle(linker, user32, "GetWindowLongPtrW", FunctionDescriptor.of(J, A, I));
        getWindow = handle(linker, user32, "GetWindow", FunctionDescriptor.of(A, A, I));
        setWindowPos = handle(linker, user32, "SetWindowPos", FunctionDescriptor.of(I, A, A, I, I, I, I, I));
        isWindow = handle(linker, user32, "IsWindow", FunctionDescriptor.of(I, A));
        setForegroundWindow = handle(linker, user32, "SetForegroundWindow", FunctionDescriptor.of(I, A));
        findWindowEx = handle(linker, user32, "FindWindowExW", FunctionDescriptor.of(A, A, A, A, A));
        isWindowVisible = handle(linker, user32, "IsWindowVisible", FunctionDescriptor.of(I, A));
        sendMessage = handle(linker, user32, "SendMessageW", FunctionDescriptor.of(J, A, I, J, J));
        getWindowThreadProcessId = handle(linker, user32, "GetWindowThreadProcessId", FunctionDescriptor.of(I, A, A));
        mapWindowPoints = handle(linker, user32, "MapWindowPoints", FunctionDescriptor.of(I, A, A, A, I));

        SymbolLookup kernel32 = SymbolLookup.libraryLookup("kernel32", Arena.global());
        openProcess = handle(linker, kernel32, "OpenProcess", FunctionDescriptor.of(A, I, I, I));
        virtualAllocEx = handle(linker, kernel32, "VirtualAllocEx", FunctionDescriptor.of(A, A, A, J, I, I));
        virtualFreeEx = handle(linker, kernel32, "VirtualFreeEx", FunctionDescriptor.of(I, A, A, J, I));
        readProcessMemory = handle(linker, kernel32, "ReadProcessMemory", FunctionDescriptor.of(I, A, A, A, J, A));
        closeHandle = handle(linker, kernel32, "CloseHandle", FunctionDescriptor.of(I, A));
    }

    private static MethodHandle handle(Linker linker, SymbolLookup lib, String name, FunctionDescriptor fd) {
        return linker.downcallHandle(lib.find(name).orElseThrow(
                () -> new IllegalStateException("user32 function not found: " + name)), fd);
    }

    @Override
    public void attach(JFrame frame) throws Exception {
        try {
            progman = (MemorySegment) findWindow.invoke(wide("Progman"), MemorySegment.NULL);
            if (isNull(progman)) {
                throw new IllegalStateException("Desktop window (Progman) not found");
            }
            board = findOwnWindow(frame);
            if (isNull(board)) {
                throw new IllegalStateException("Board window handle not found");
            }
            setWindowLongPtr.invoke(board, GWLP_HWNDPARENT, progman.address());
            MemorySegment owner = (MemorySegment) getWindow.invoke(board, GW_OWNER);
            if (owner.address() != progman.address()) {
                throw new IllegalStateException("Could not set the desktop as owner");
            }
            ensureNoActivate();
            sendToBack();
        } catch (Exception e) {
            throw e;
        } catch (Throwable t) {
            throw new IllegalStateException("Native call failed: " + t, t);
        }
    }

    @Override
    public boolean stillAttached() {
        try {
            if ((int) isWindow.invoke(progman) == 0 || (int) isWindow.invoke(board) == 0) {
                return false;
            }
            ensureNoActivate(); // Java may rewrite the extended style (e.g. for transparency)
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * WS_EX_NOACTIVATE: clicking the board does not activate it, so Windows does not raise it
     * above other windows. Buttons and checkboxes still receive the clicks.
     */
    private void ensureNoActivate() throws Throwable {
        if (typing) {
            return; // switched off on purpose while typing in a memo
        }
        long ex = (long) getWindowLongPtr.invoke(board, GWL_EXSTYLE);
        if ((ex & WS_EX_NOACTIVATE) == 0) {
            setWindowLongPtr.invoke(board, GWL_EXSTYLE, ex | WS_EX_NOACTIVATE);
        }
    }

    /**
     * Typing in a memo: without WS_EX_NOACTIVATE the board can become the active window and get
     * keys. It is activated (allowed, because the user just clicked it) and then put back at the
     * bottom, so it does not cover other windows.
     */
    @Override
    public boolean beginKeyboardInput() {
        try {
            typing = true;
            long ex = (long) getWindowLongPtr.invoke(board, GWL_EXSTYLE);
            setWindowLongPtr.invoke(board, GWL_EXSTYLE, ex & ~WS_EX_NOACTIVATE);
            int ok = (int) setForegroundWindow.invoke(board);
            sendToBack();
            if (ok == 0) {
                endKeyboardInput();
                return false;
            }
            return true;
        } catch (Throwable t) {
            endKeyboardInput();
            return false;
        }
    }

    @Override
    public void endKeyboardInput() {
        typing = false;
        try {
            ensureNoActivate();
        } catch (Throwable t) {
            System.err.println("[Win32Desktop] Cannot restore WS_EX_NOACTIVATE: " + t);
        }
    }

    @Override
    public void sendToBack() {
        moveInStack(HWND_BOTTOM);
    }

    @Override
    public void bringForward() {
        moveInStack(HWND_TOP);
    }

    private void moveInStack(long after) {
        try {
            setWindowPos.invoke(board, MemorySegment.ofAddress(after), 0, 0, 0, 0,
                    SWP_NOMOVE | SWP_NOSIZE | SWP_NOACTIVATE);
        } catch (Throwable t) {
            System.err.println("[Win32Desktop] SetWindowPos failed: " + t);
        }
    }

    /**
     * Reads the desktop icon positions from the desktop list view (SysListView32 in Explorer).
     * The list view writes positions into memory of Explorer's process, so a small buffer is
     * borrowed there, read back, and freed. Nothing on the desktop is changed.
     */
    @Override
    public List<Rectangle> desktopIconBounds() {
        List<Rectangle> result = new ArrayList<>();
        MemorySegment process = MemorySegment.NULL;
        MemorySegment remote = MemorySegment.NULL;
        try {
            MemorySegment listView = findIconListView();
            if (isNull(listView) || (int) isWindowVisible.invoke(listView) == 0) {
                return result; // icons hidden ("Show desktop icons" is off)
            }
            int count = (int) (long) sendMessage.invoke(listView, LVM_GETITEMCOUNT, 0L, 0L);
            if (count <= 0) {
                return result;
            }
            long spacing = (long) sendMessage.invoke(listView, LVM_GETITEMSPACING, 0L, 0L);
            int iconW = (int) (spacing & 0xFFFF);
            int iconH = (int) ((spacing >> 16) & 0xFFFF);

            MemorySegment pid = arena.allocate(I);
            getWindowThreadProcessId.invoke(listView, pid);
            process = (MemorySegment) openProcess.invoke(PROCESS_VM_OPERATION | PROCESS_VM_READ, 0, pid.get(I, 0));
            if (isNull(process)) {
                return result;
            }
            remote = (MemorySegment) virtualAllocEx.invoke(process, MemorySegment.NULL, 16L, MEM_COMMIT_RESERVE, PAGE_READWRITE);
            if (isNull(remote)) {
                return result;
            }
            double scale = screenScale();
            MemorySegment point = arena.allocate(8, 4);
            MemorySegment read = arena.allocate(J);
            for (int i = 0; i < count; i++) {
                sendMessage.invoke(listView, LVM_GETITEMPOSITION, (long) i, remote.address());
                if ((int) readProcessMemory.invoke(process, remote, point, 8L, read) == 0) {
                    continue;
                }
                mapWindowPoints.invoke(listView, MemorySegment.NULL, point, 1); // list view -> screen
                int x = point.get(I, 0);
                int y = point.get(I, 4);
                result.add(new Rectangle((int) Math.floor(x / scale), (int) Math.floor(y / scale),
                        (int) Math.ceil(iconW / scale), (int) Math.ceil(iconH / scale)));
            }
        } catch (Throwable t) {
            System.err.println("[Win32Desktop] Cannot read icon positions: " + t);
        } finally {
            try {
                if (!isNull(remote)) {
                    virtualFreeEx.invoke(process, remote, 0L, MEM_RELEASE);
                }
                if (!isNull(process)) {
                    closeHandle.invoke(process);
                }
            } catch (Throwable ignored) {
                // nothing more to clean up
            }
        }
        return result;
    }

    private MemorySegment findIconListView() throws Throwable {
        MemorySegment host = (MemorySegment) findWindow.invoke(wide("Progman"), MemorySegment.NULL);
        MemorySegment defView = isNull(host) ? MemorySegment.NULL : defViewOf(host);
        MemorySegment w = MemorySegment.NULL;
        while (isNull(defView)) { // desktop split by a wallpaper tool: icons live in a WorkerW
            w = (MemorySegment) findWindowEx.invoke(MemorySegment.NULL, w, wide("WorkerW"), MemorySegment.NULL);
            if (isNull(w)) {
                return MemorySegment.NULL;
            }
            defView = defViewOf(w);
        }
        return (MemorySegment) findWindowEx.invoke(defView, MemorySegment.NULL, wide("SysListView32"), MemorySegment.NULL);
    }

    private MemorySegment defViewOf(MemorySegment parent) throws Throwable {
        return (MemorySegment) findWindowEx.invoke(parent, MemorySegment.NULL, wide("SHELLDLL_DefView"), MemorySegment.NULL);
    }

    /** Windows gives physical pixels; Java screen coordinates are scaled by the display scale. */
    private static double screenScale() {
        try {
            return GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice()
                    .getDefaultConfiguration().getDefaultTransform().getScaleX();
        } catch (RuntimeException e) {
            return 1.0;
        }
    }

    /** Swing has no public API for the HWND, so search for a frame with a temporary unique title. */
    private MemorySegment findOwnWindow(JFrame frame) throws Throwable {
        String original = frame.getTitle();
        String unique = "DesktopPlanner-" + ProcessHandle.current().pid() + "-" + System.nanoTime();
        frame.setTitle(unique);
        try {
            return (MemorySegment) findWindow.invoke(wide("SunAwtFrame"), wide(unique));
        } finally {
            frame.setTitle(original);
        }
    }

    /** UTF-16 string for the Windows "W" functions. */
    private MemorySegment wide(String s) {
        MemorySegment m = arena.allocate((s.length() + 1) * 2L, 2);
        for (int i = 0; i < s.length(); i++) {
            m.setAtIndex(ValueLayout.JAVA_CHAR, i, s.charAt(i));
        }
        m.setAtIndex(ValueLayout.JAVA_CHAR, s.length(), '\0');
        return m;
    }

    private static boolean isNull(MemorySegment s) {
        return s == null || s.address() == 0;
    }
}
