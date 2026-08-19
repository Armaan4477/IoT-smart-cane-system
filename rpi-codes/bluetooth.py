#!/usr/bin/env python3
"""
bluetooth.py — Raspberry Pi Bluetooth RFCOMM server.

Uses Python built-in socket (AF_BLUETOOTH / BTPROTO_RFCOMM).
No external Bluetooth library required.

ONE-TIME SETUP on the Pi (run these once, then reboot is not needed):
    sudo bluetoothctl system-alias DrishtiStick
    sudo hciconfig hci0 piscan

Then pair the Android phone:
    On Android: Settings → Bluetooth → Scan → pair "DrishtiStick"

Protocol:
    Listens on RFCOMM_CHANNEL (fixed, must match BluetoothService.RFCOMM_CHANNEL).
    Sends newline-delimited JSON when command changes:
        {"command": "LEFT"}\n
    Commands: "LEFT", "RIGHT", "FORWARD", "STOP"

Standalone test:
    ~/sih/venv/bin/python ~/sih/bluetooth.py
"""

import json
import socket
import threading
import time

RFCOMM_CHANNEL = 3      # Fixed channel — must match Android BluetoothService.RFCOMM_CHANNEL
SEND_INTERVAL  = 0.4    # Seconds between send ticks


class BluetoothServer:
    def __init__(self):
        self._lock            = threading.Lock()
        self._current_command = "FORWARD"
        self._client_sock     = None
        self._running         = False
        self._server_thread   = None

    # ── Public API ────────────────────────────────────────────────────────────

    def start(self):
        """Start the RFCOMM server in a daemon background thread."""
        self._running = True
        self._server_thread = threading.Thread(
            target=self._serve_loop, daemon=True, name="BT-Server"
        )
        self._server_thread.start()
        print(f"[BT] RFCOMM server started on channel {RFCOMM_CHANNEL}.")

    def stop(self):
        self._running = False
        self._close_client()

    def send_command(self, command: str):
        """Thread-safe: update the command to broadcast to the Android app."""
        with self._lock:
            self._current_command = command

    # ── Internal ──────────────────────────────────────────────────────────────

    def _serve_loop(self):
        while self._running:
            server_sock = None
            client_sock = None
            try:
                server_sock = socket.socket(
                    socket.AF_BLUETOOTH, socket.SOCK_STREAM, socket.BTPROTO_RFCOMM
                )
                server_sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
                server_sock.bind((socket.BDADDR_ANY, RFCOMM_CHANNEL))
                server_sock.listen(1)
                print(f"[BT] Waiting for Android on RFCOMM channel {RFCOMM_CHANNEL}...")

                client_sock, addr = server_sock.accept()
                print(f"[BT] Android connected from {addr}")
                with self._lock:
                    self._client_sock = client_sock

                self._stream_commands(client_sock)

            except Exception as e:
                if self._running:
                    print(f"[BT] Server error: {e}")
            finally:
                for s in (client_sock, server_sock):
                    if s is not None:
                        try:
                            s.close()
                        except Exception:
                            pass
                with self._lock:
                    self._client_sock = None
                if self._running:
                    print("[BT] Restarting server in 3 s...")
                    time.sleep(3)

    def _stream_commands(self, sock: socket.socket):
        """Keep sending the current command to the Android app whenever it changes."""
        last_sent = None
        while self._running:
            try:
                with self._lock:
                    cmd = self._current_command
                if cmd != last_sent:
                    payload = json.dumps({"command": cmd}) + "\n"
                    sock.sendall(payload.encode("utf-8"))
                    last_sent = cmd
                    print(f"[BT] → {cmd}")
                time.sleep(SEND_INTERVAL)
            except Exception as e:
                print(f"[BT] Send error: {e}")
                break

    def _close_client(self):
        with self._lock:
            if self._client_sock is not None:
                try:
                    self._client_sock.close()
                except Exception:
                    pass
                self._client_sock = None


# ── Standalone cycling test ───────────────────────────────────────────────────

if __name__ == "__main__":
    import itertools

    print("=== Bluetooth RFCOMM Server Test ===")
    print("One-time Pi setup:")
    print("  sudo bluetoothctl system-alias DrishtiStick")
    print("  sudo hciconfig hci0 piscan")
    print("Then pair Android phone and open the Drishti app.")
    print("Press CTRL+C to stop.\n")

    server = BluetoothServer()
    server.start()

    cycle_cmds = itertools.cycle(["LEFT", "FORWARD", "RIGHT", "STOP"])
    try:
        for cmd in cycle_cmds:
            server.send_command(cmd)
            time.sleep(3)
    except KeyboardInterrupt:
        print("\nStopping...")
    finally:
        server.stop()
