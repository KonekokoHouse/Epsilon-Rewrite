import socket
import struct
import os
import sys

def send_file(host, port, filepath):
    """
    Send a file via TCP to a specified host and port.
    Protocol Format:
    1. 4-byte big-endian integer: Filename length
    2. Filename (UTF-8 encoded)
    3. File binary content (until connection is closed)
    """
    if not os.path.exists(filepath):
        print(f"Error: File {filepath} does not exist.")
        return

    filename = os.path.basename(filepath)
    filename_bytes = filename.encode('utf-8')
    filename_len = len(filename_bytes)

    try:
        print(f"Connecting to {host}:{port}...")
        with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
            s.connect((host, port))
            
            # 1. Send filename length
            s.sendall(struct.pack('>I', filename_len))
            # 2. Send filename
            s.sendall(filename_bytes)
            
            # 3. Send file content
            print(f"Sending file: {filename} ({os.path.getsize(filepath)} bytes)")
            with open(filepath, 'rb') as f:
                while True:
                    chunk = f.read(8192)
                    if not chunk:
                        break
                    s.sendall(chunk)
            
            print(f"Successfully sent {filename} to {host}:{port}")
    except ConnectionRefusedError:
        print(f"Error: Could not connect to {host}:{port}. Ensure the server is running and port forwarding is configured.")
    except Exception as e:
        print(f"An error occurred while sending the file: {e}")

if __name__ == "__main__":
    if len(sys.argv) < 4:
        print("Usage: python tcp_client.py <host> <port> <filepath>")
        print("Example: python tcp_client.py 12.34.56.78 40020 test.jpg")
    else:
        host = sys.argv[1]
        port = int(sys.argv[2])
        filepath = sys.argv[3]
        send_file(host, port, filepath)
