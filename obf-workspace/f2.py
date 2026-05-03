import random
import string
import os
from pathlib import Path

def generate_names():
    prefix = "zako"
    chars = string.ascii_letters + string.digits
    names = []
    for _ in range(1001):
        suffix = ''.join(random.choice(chars) for _ in range(74))
        names.append(prefix + suffix)
    
    # Use Path to construct the file path relative to the script location
    script_dir = Path(__file__).parent
    file_path = script_dir / "ZKM-25.0.0-Cracked" / "ZKM 25.0.0" / "names.txt"
    
    # Ensure the directory exists
    file_path.parent.mkdir(parents=True, exist_ok=True)
    
    with open(file_path, "w", encoding="utf-8") as f:
        f.write("\n".join(names))
    print(f"Successfully generated 1000 names to {file_path}")

if __name__ == "__main__":
    generate_names()
