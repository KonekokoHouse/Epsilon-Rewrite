#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Fixed one-click JAR mixin/class renamer

Usage (example dry-run):
python mc_mixin_obfuscator_fixed.py --jar "..\\ZKM-21.0.0-Cracked\\ZKM 21.0.0\\Kawaii-1S.jar" --pkg dev.kizuna.asm --out Kawaii-S2.jar --configs fabric.mod.json kawaii-refmap.json kawaii.accesswidener kawaii.mixins.json --backup obf_backups --dry-run
"""

import argparse
import zipfile
import tempfile
import shutil
import struct
import secrets
import string
import json
from pathlib import Path

ALNUM = string.ascii_letters + string.digits
OBF_PREFIX = "m$$0"
OBF_LEN = 147
CLASS_MAGIC = b"\xCA\xFE\xBA\xBE"

def gen_obf():
    return OBF_PREFIX + ''.join(secrets.choice(ALNUM) for _ in range(OBF_LEN))

# --- binary readers ---
def read_u1(b, pos):
    return b[pos], pos + 1

def read_u2(b, pos):
    return struct.unpack_from(">H", b, pos)[0], pos + 2

def read_u4(b, pos):
    return struct.unpack_from(">I", b, pos)[0], pos + 4

def write_u2(v):
    return struct.pack(">H", v)

def write_u4(v):
    return struct.pack(">I", v)

# --- parse constant pool robustly ---
def parse_constant_pool(b, pos, cp_count):
    """
    Parse constant pool entries. Return (entries_list, pos_after_cp)
    entries_list: list of dicts; placeholders for long/double are present with {'placeholder': True}
    For Utf8 entries: {'tag':1, 'bytes': b'...', 'str': decoded_or_None}
    For other supported tags: {'tag':tag, 'bytes': raw_bytes}
    """
    entries = []
    i = 1
    while i < cp_count:
        tag = b[pos]
        pos += 1
        if tag == 1:  # Utf8
            length, pos = read_u2(b, pos)
            data = b[pos:pos+length]; pos += length
            try:
                decoded = data.decode('utf-8', errors='surrogatepass')
            except Exception:
                decoded = None
            entries.append({'tag': 1, 'bytes': data, 'str': decoded})
        elif tag in (3,4):  # Integer, Float
            data = b[pos:pos+4]; pos += 4
            entries.append({'tag': tag, 'bytes': data})
        elif tag in (5,6):  # Long, Double (take two cp entries)
            data = b[pos:pos+8]; pos += 8
            entries.append({'tag': tag, 'bytes': data})
            # add placeholder for the next index
            entries.append({'placeholder': True})
            i += 2
            continue
        elif tag in (7,8,16):  # Class, String, MethodType -> u2
            data = b[pos:pos+2]; pos += 2
            entries.append({'tag': tag, 'bytes': data})
        elif tag in (9,10,11,12,18):  # Fieldref, Methodref, InterfaceMethodref, NameAndType, InvokeDynamic -> u2+u2
            data = b[pos:pos+4]; pos += 4
            entries.append({'tag': tag, 'bytes': data})
        elif tag == 15:  # MethodHandle: u1 + u2
            data = b[pos:pos+3]; pos += 3
            entries.append({'tag': tag, 'bytes': data})
        else:
            # Unknown tag: raise - safer to fail than corrupt
            raise ValueError(f"Unknown constant pool tag {tag}")
        i += 1
    return entries, pos

def serialize_constant_pool(entries):
    out = bytearray()
    for e in entries:
        if e.get('placeholder'):
            # placeholder slots are not serialized directly; they correspond to the second slot of long/double
            # But in the original class file long/double are serialized as tag + 8 bytes; we serialized that as a single entry above
            # So placeholders should not be serialized separately.
            continue
        tag = e['tag']
        out.append(tag)
        if tag == 1:
            data = e.get('bytes', b'')
            out += write_u2(len(data))
            out += data
        else:
            out += e.get('bytes', b'')
    return bytes(out)

# --- extract internal class name ---
def extract_internal_name(class_bytes):
    """
    Return internal name like dev/kizuna/asm/MyClass or None if cannot parse.
    """
    try:
        b = class_bytes
        if b[:4] != CLASS_MAGIC:
            return None
        pos = 4
        # minor, major
        _, pos = read_u2(b, pos)
        _, pos = read_u2(b, pos)
        cp_count, pos = read_u2(b, pos)
        entries, pos_after_cp = parse_constant_pool(b, pos, cp_count)
        # after cp
        pos2 = pos_after_cp
        # access_flags (u2)
        _, pos2 = read_u2(b, pos2)
        this_class_index, pos2 = read_u2(b, pos2)
        # Build cp indexing: cp indexes start at 1; entries list corresponds to indices 1..cp_count-1 including placeholders
        # So entries[this_class_index - 1] should be the Class_info (tag 7)
        if this_class_index - 1 < 0 or this_class_index - 1 >= len(entries):
            return None
        class_info = entries[this_class_index - 1]
        if class_info.get('tag') != 7:
            return None
        name_index = struct.unpack(">H", class_info['bytes'])[0]
        if name_index - 1 < 0 or name_index - 1 >= len(entries):
            return None
        name_ent = entries[name_index - 1]
        if name_ent.get('tag') != 1:
            return None
        s = name_ent.get('str')
        return s
    except Exception:
        return None

# --- modify class bytes (rewrite Utf8 entries) ---
def modify_class_bytes(class_bytes, mapping_internal):
    """
    mapping_internal: dict old_internal -> new_internal (slash form)
    returns new bytes if changed, else None
    """
    b = class_bytes
    if b[:4] != CLASS_MAGIC:
        return None
    pos = 4
    minor, pos = read_u2(b, pos)
    major, pos = read_u2(b, pos)
    cp_count, pos = read_u2(b, pos)
    entries, pos_after_cp = parse_constant_pool(b, pos, cp_count)

    # prepare replacement list (old -> new), longest-old-first
    repls = []
    for old, new in mapping_internal.items():
        repls.append((old, new))
        repls.append((old.replace('/', '.'), new.replace('/', '.')))
        repls.append(("L" + old + ";", "L" + new + ";"))
    repls = sorted(repls, key=lambda x: -len(x[0]))

    changed = False
    for ent in entries:
        if ent.get('placeholder'):
            continue
        if ent.get('tag') != 1:
            continue
        s = ent.get('str')
        if not s:
            continue
        new_s = s
        for old, new in repls:
            if old in new_s:
                new_s = new_s.replace(old, new)
        if new_s != s:
            ent['str'] = new_s
            ent['bytes'] = new_s.encode('utf-8', errors='surrogatepass')
            changed = True

    if not changed:
        return None

    # rebuild class file
    out = bytearray()
    out += CLASS_MAGIC
    out += write_u2(minor)
    out += write_u2(major)
    out += write_u2(cp_count)
    out += serialize_constant_pool(entries)
    out += b[pos_after_cp:]
    return bytes(out)

# --- config update inside extracted tree ---
def update_config_files_in_tree(extracted_root: Path, mapping_internal, config_names, dry_run=False):
    """
    Improved config updater:
    - mapping_internal: old_internal (slash) -> new_internal (slash)
    - For JSON files that contain a 'package' field (like mixins json), will resolve relative names
      (e.g. 'mixins.MixinFoo' with package 'dev.kizuna.asm' -> 'dev.kizuna.asm.mixins.MixinFoo')
      and replace using the mapping. If the new full name remains under the same package, write it
      back in relative form (so file style is preserved).
    - For non-JSON or parse-fail files, fall back to plain text replacement (both slash and dotted).
    """
    # prepare dotted mapping for quick lookup
    mapping_dot = {old.replace('/', '.'): new.replace('/', '.') for old, new in mapping_internal.items()}
    mapping_slash = dict(mapping_internal)  # keep slash form too

    # longest-first keys for fallback text replacement
    text_repls = []
    for old_slash, new_slash in mapping_slash.items():
        text_repls.append((old_slash, new_slash))
        text_repls.append((old_slash.replace('/', '.'), new_slash.replace('/', '.')))
        text_repls.append(("L" + old_slash + ";", "L" + new_slash + ";"))
    text_repls = sorted(text_repls, key=lambda x: -len(x[0]))

    for cfgname in config_names:
        for p in extracted_root.rglob(cfgname):
            try:
                raw = p.read_text(encoding='utf-8', errors='ignore')
            except Exception:
                continue

            # Try JSON path first
            parsed = None
            try:
                parsed = json.loads(raw)
            except Exception:
                parsed = None

            if parsed is None:
                # fallback: plain text replacement (as before)
                new_text = raw
                for old, new in text_repls:
                    new_text = new_text.replace(old, new)
                if new_text != raw:
                    if dry_run:
                        print(f"[DRY] would update text file {p}")
                    else:
                        p.write_text(new_text, encoding='utf-8')
                continue

            # parsed JSON - attempt intelligent replacement
            changed = False

            # If it's a dict and has 'package', handle relative class lists
            if isinstance(parsed, dict) and 'package' in parsed and isinstance(parsed['package'], str):
                pkg = parsed['package']  # dotted form e.g. dev.kizuna.asm

                # helper to convert an entry string -> possibly replaced string
                def replace_entry_str(entry_str):
                    nonlocal changed
                    if not isinstance(entry_str, str):
                        return entry_str
                    # If entry already starts with the package, use as-is; else treat as relative to package
                    if entry_str.startswith(pkg + "."):
                        full_old = entry_str
                    else:
                        full_old = pkg + "." + entry_str
                    # if mapping has this full_old
                    if full_old in mapping_dot:
                        new_full = mapping_dot[full_old]
                        # if the new full is still in same package, convert back to relative to preserve style
                        if new_full.startswith(pkg + "."):
                            new_rel = new_full[len(pkg) + 1:]
                            changed = True
                            return new_rel
                        else:
                            # otherwise keep fully qualified
                            changed = True
                            return new_full
                    # else try plain direct replacement if entry_str itself equals some dotted mapping key
                    if entry_str in mapping_dot:
                        new_full = mapping_dot[entry_str]
                        # try to make relative if same package
                        if new_full.startswith(pkg + "."):
                            new_rel = new_full[len(pkg) + 1:]
                            changed = True
                            return new_rel
                        else:
                            changed = True
                            return new_full
                    return entry_str

                # walk through parsed object: for common keys that contain lists of class names
                # typical keys: "client", "mixins", "server" etc.
                for key, val in list(parsed.items()):
                    if isinstance(val, list):
                        new_list = []
                        replaced_any = False
                        for item in val:
                            if isinstance(item, str):
                                new_item = replace_entry_str(item)
                                if new_item != item:
                                    replaced_any = True
                                new_list.append(new_item)
                            else:
                                new_list.append(item)
                        if replaced_any:
                            parsed[key] = new_list

                # Also check string fields that may reference a full class (e.g., "refmap")
                for key, val in list(parsed.items()):
                    if isinstance(val, str):
                        # try to replace exact dotted matches
                        if val in mapping_dot:
                            parsed[key] = mapping_dot[val]
                            changed = True
                        else:
                            # or if val is relative to package
                            if not val.startswith(pkg + "."):
                                full_val = pkg + "." + val
                            else:
                                full_val = val
                            if full_val in mapping_dot:
                                new_full = mapping_dot[full_val]
                                if new_full.startswith(pkg + "."):
                                    parsed[key] = new_full[len(pkg) + 1:]
                                else:
                                    parsed[key] = new_full
                                changed = True

            else:
                # Generic JSON: replace any string value that exactly matches a dotted class name in mapping
                def walk_and_replace(obj):
                    nonlocal changed
                    if isinstance(obj, dict):
                        for k, v in obj.items():
                            obj[k] = walk_and_replace(v)
                        return obj
                    if isinstance(obj, list):
                        return [walk_and_replace(x) for x in obj]
                    if isinstance(obj, str):
                        if obj in mapping_dot:
                            changed = True
                            return mapping_dot[obj]
                        # also accept slash-form matches
                        if obj in mapping_slash:
                            changed = True
                            return mapping_slash[obj].replace('/', '.')
                        return obj
                    return obj
                parsed = walk_and_replace(parsed)

            if changed:
                new_raw = json.dumps(parsed, indent=2, ensure_ascii=False)
                if dry_run:
                    print(f"[DRY] would write updated JSON to {p}")
                else:
                    p.write_text(new_raw, encoding='utf-8')
            else:
                # No JSON-level changes; as fallback also perform plain text replacements (covers other occurrences)
                new_text = raw
                for old, new in text_repls:
                    new_text = new_text.replace(old, new)
                if new_text != raw:
                    if dry_run:
                        print(f"[DRY] would update text (fallback) {p}")
                    else:
                        p.write_text(new_text, encoding='utf-8')

# --- main jar flow ---
def run_on_jar(jar_path: Path, pkg_list, out_path: Path, config_names, backup_dir: Path, dry_run=False):
    """
    pkg_list: either a single string 'dev.pkg.name' or a list ['dev.pkg1.name', 'dev.pkg2.name']
    """
    if not jar_path.exists():
        raise FileNotFoundError(jar_path)
    
    # normalize to list
    if isinstance(pkg_list, str):
        pkg_list = [pkg_list]
    
    backup_dir.mkdir(parents=True, exist_ok=True)
    shutil.copy2(jar_path, backup_dir / jar_path.name)

    with tempfile.TemporaryDirectory() as td:
        td = Path(td)
        with zipfile.ZipFile(jar_path, 'r') as zin:
            zin.extractall(td)

        pkg_internals = [pkg.replace('.', '/') for pkg in pkg_list]
        class_files = list(td.rglob('*.class'))

        classes = {}
        for cf in class_files:
            try:
                b = cf.read_bytes()
            except Exception:
                continue
            internal = extract_internal_name(b)
            if not internal:
                # skip unparseable class
                continue
            # check if internal class belongs to any of the target packages
            for pkg_internal in pkg_internals:
                if internal == pkg_internal or internal.startswith(pkg_internal + '/'):
                    classes[cf] = internal
                    break

        print(f"Found {len(class_files)} .class files; will process {len(classes)} under {pkg_list}")

        # build mapping old_internal -> new_internal
        mapping = {}
        for old_internal in sorted(set(classes.values())):
            last = old_internal.split('/')[-1]
            base = '/'.join(old_internal.split('/')[:-1])
            if '$' in last:
                # Handle inner classes: keep the same outer$inner structure if possible, 
                # but here we generate a new name for the whole class to be safe.
                # Actually, ZKM-style would be better, but let's stick to the current logic 
                # and just ensure we don't break things.
                parts = last.split('$', 1)
                new_last = gen_obf() + '$' + parts[1]
            else:
                new_last = gen_obf()
            new_internal = (base + '/' + new_last) if base else new_last
            mapping[old_internal] = new_internal

        print("Mapping preview (sample):")
        for i,(k,v) in enumerate(mapping.items()):
            print(f"  {k} -> {v}")
            if i >= 40:
                break

        if dry_run:
            return mapping

        # --- CRITICAL FIX: Process ALL class files to remap references ---
        print(f"Remapping references in all {len(class_files)} class files...")
        for cf in class_files:
            b = cf.read_bytes()
            new_b = modify_class_bytes(b, mapping)
            if new_b is not None:
                cf.write_bytes(new_b)
                # print(f"  Updated references in {cf.name}")

        # --- Now rename/move the classes that belong to the target package ---
        for cf, internal in classes.items():
            new_internal = mapping.get(internal)
            if not new_internal:
                continue
            
            # move file to new path according to new_internal
            new_path = td.joinpath(*new_internal.split('/'))
            new_path_parent = new_path.parent
            new_path_parent.mkdir(parents=True, exist_ok=True)
            target_file = new_path.with_suffix('.class')
            
            if cf != target_file:
                if target_file.exists():
                    target_file.unlink()
                cf.rename(target_file)
                # print(f"  Moved {internal} -> {new_internal}")

        # update config files
        update_config_files_in_tree(td, mapping, config_names, dry_run=False)

        # remove signature files
        meta = td / 'META-INF'
        if meta.exists() and meta.is_dir():
            for f in meta.iterdir():
                if f.suffix.upper() in ('.SF', '.RSA', '.DSA'):
                    try:
                        f.unlink()
                        print(f"Removed signature {f.name}")
                    except Exception:
                        pass

        # repackage
        with zipfile.ZipFile(out_path, 'w', zipfile.ZIP_DEFLATED) as zout:
            for fp in sorted(td.rglob('*')):
                if fp.is_file():
                    arcname = str(fp.relative_to(td)).replace('\\', '/')
                    zout.write(fp, arcname)

        return mapping

# --- CLI ---
def main():
    ap = argparse.ArgumentParser(description='One-click JAR mixin obfuscator (fixed)')
    ap.add_argument('--jar', required=True)
    ap.add_argument('--pkg', nargs='+', required=True, help='One or more package names (e.g., dev.sakura.client.mixins dev.sakura.fabric.mixins)')
    ap.add_argument('--out', default=None)
    ap.add_argument('--configs', nargs='*', default=['fabric.mod.json','sakura.mixins.json','sakura-refmap.json','sakura.accesswidener'])
    ap.add_argument('--backup', default='obf_jar_backups')
    ap.add_argument('--dry-run', action='store_true')
    args = ap.parse_args()

    jar_path = Path(args.jar)
    out_path = Path(args.out) if args.out else jar_path.with_name(jar_path.stem + '.obf.jar')
    backup_dir = Path(args.backup)
    # args.pkg is now a list
    mapping = run_on_jar(jar_path, args.pkg, out_path, args.configs, backup_dir, dry_run=args.dry_run)

    if args.dry_run:
        print("=== DRY RUN mapping preview ===")
        print(json.dumps(mapping, indent=2, ensure_ascii=False))
    else:
        print("Done. output:", out_path)

if __name__ == '__main__':
    main()
