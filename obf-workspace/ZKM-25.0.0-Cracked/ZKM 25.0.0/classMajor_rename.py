#!/usr/bin/env python3
"""
classMajor_rename_outfile.py

把 jar 中的 .class 的 major version 从指定值替换为另一个值（默认 65 -> 61, 即 Java 21 -> Java 17）。
**与原脚本不同**：修改后的 jar 不会替换原文件，默认会另存为 "<originalname>-to<to_major>.jar"。
如果目标文件已存在，会自动编号为 "<name>-to<to_major>.jar.1", ".2", ... 除非指定 --replace。

特性：
 - 自动删除 META-INF/*.SF|*.RSA|*.DSA|*.EC（签名会失效）
 - 尽量保留 zip 条目的 metadata（compress_type/date_time/external_attr/extra/comment）
 - 支持 --dry-run（仅报告，不写文件）
 - 支持递归扫描目录：-r / --recursive
 - 支持 verbose 输出

用法示例：
    # 在当前目录把所有 jar 中 major 65 -> 61，输出到新文件（不覆盖原）
    python classMajor_rename_outfile.py

    # 指定单个 jar，改成 61 -> 52，覆盖原文件（谨慎）
    python classMajor_rename_outfile.py path/to/a.jar --from 61 --to 52 --replace

    # 递归处理目录，指定输出目录
    python classMajor_rename_outfile.py somedir -r --out-dir ./out --from 65 --to 61

"""
import argparse
import os
import sys
import tempfile
import shutil
import struct
import re
from zipfile import ZipFile, ZipInfo, ZIP_DEFLATED

MAGIC = b'\xCA\xFE\xBA\xBE'
SIG_PATTERN = re.compile(r'^META-INF/[^/]+\.(SF|RSA|DSA|EC)$', re.IGNORECASE)

def unique_path(path):
    """如果 path 存在，返回带序号的新路径（path.1, path.2, ...）"""
    if not os.path.exists(path):
        return path
    i = 1
    while True:
        candidate = f"{path}.{i}"
        if not os.path.exists(candidate):
            return candidate
        i += 1

def process_jar(path, from_major=65, to_major=61, out_dir=None, replace=False, dry_run=False, verbose=False):
    total_classes = 0
    changed_classes = 0
    removed_signatures = []
    entries_written = 0

    if verbose:
        print(f"[>] Processing: {path}")

    with ZipFile(path, 'r') as zin:
        # create temp file for new jar
        tmp_fd, tmp_path = tempfile.mkstemp(suffix='.jar')
        os.close(tmp_fd)
        try:
            with ZipFile(tmp_path, 'w') as zout:
                for info in zin.infolist():
                    name = info.filename
                    data = zin.read(name)
                    write_data = data

                    # remove signature files
                    if SIG_PATTERN.match(name):
                        removed_signatures.append(name)
                        if verbose:
                            print(f"    [-] Removing signature file: {name}")
                        continue

                    # preserve directories
                    if name.endswith('/'):
                        zi = ZipInfo(name)
                        zi.date_time = info.date_time
                        zi.compress_type = info.compress_type
                        zi.external_attr = info.external_attr
                        zi.create_system = info.create_system
                        try:
                            zi.extra = info.extra
                        except Exception:
                            pass
                        try:
                            zi.comment = info.comment
                        except Exception:
                            pass
                        zout.writestr(zi, b'')
                        entries_written += 1
                        continue

                    # only touch .class files with valid magic
                    if name.endswith('.class') and len(data) >= 8 and data[0:4] == MAGIC:
                        total_classes += 1
                        major = struct.unpack('>H', data[6:8])[0]
                        if major == from_major:
                            if not dry_run:
                                new_major_bytes = (to_major).to_bytes(2, 'big')
                                write_data = data[:6] + new_major_bytes + data[8:]
                            changed_classes += 1
                            if verbose:
                                print(f"    [*] Will change {name}: major {major} -> {to_major}")

                    # preserve metadata
                    new_info = ZipInfo(name)
                    new_info.date_time = info.date_time
                    new_info.compress_type = info.compress_type
                    new_info.external_attr = info.external_attr
                    new_info.create_system = info.create_system
                    try:
                        new_info.extra = info.extra
                    except Exception:
                        pass
                    try:
                        new_info.comment = info.comment
                    except Exception:
                        pass

                    zout.writestr(new_info, write_data)
                    entries_written += 1

            # finished writing tmp jar
            if dry_run:
                os.remove(tmp_path)
                print(f"[DRY-RUN] {path}: total .class={total_classes}, matched_to_change={changed_classes}, removed_signatures={len(removed_signatures)}")
                if removed_signatures and verbose:
                    for s in removed_signatures:
                        print(f"    removed: {s}")
                return True

            if changed_classes == 0 and not removed_signatures:
                os.remove(tmp_path)
                print(f"[-] No changes for {path} (checked {total_classes} .class files).")
                return False

            # determine output path
            if replace:
                # replace original but keep a .bak copy if possible
                bak = path + '.bak'
                if os.path.exists(bak):
                    bak = unique_path(bak)
                shutil.move(path, bak)
                out_path = path
                shutil.move(tmp_path, out_path)
                print(f"[+] Replaced {path}: total .class={total_classes}, changed={changed_classes}, removed_signatures={len(removed_signatures)}. Backup: {bak}")
                if removed_signatures:
                    print(f"    Note: removed signature files ({len(removed_signatures)}). Jar signatures are invalid after editing.")
                return True
            else:
                # write to out_dir / new filename
                base = os.path.basename(path)
                name, ext = os.path.splitext(base)
                new_name = f"{name}-to{to_major}{ext}"
                if out_dir:
                    out_path = os.path.join(out_dir, new_name)
                else:
                    out_path = os.path.join(os.path.dirname(path), new_name)
                # avoid overwriting existing
                out_path = unique_path(out_path)
                shutil.move(tmp_path, out_path)
                print(f"[+] Wrote {out_path}: total .class={total_classes}, changed={changed_classes}, removed_signatures={len(removed_signatures)}.")
                if removed_signatures:
                    print(f"    Note: removed signature files ({len(removed_signatures)}). Jar signatures are invalid after editing.")
                return True

        except Exception as e:
            if os.path.exists(tmp_path):
                os.remove(tmp_path)
            raise

def find_jars(root='.', recursive=False):
    if recursive:
        for dirpath, dirnames, filenames in os.walk(root):
            for fn in filenames:
                if fn.lower().endswith('.jar'):
                    yield os.path.join(dirpath, fn)
    else:
        for fn in os.listdir(root):
            if fn.lower().endswith('.jar') and os.path.isfile(fn):
                yield os.path.join(root, fn)

def main():
    parser = argparse.ArgumentParser(description="Write modified jar as a new file (default) or replace original (with .bak).")
    parser.add_argument('paths', nargs='*', help='jar file(s) or directories to process. If omitted, scan current directory.')
    parser.add_argument('--from', dest='from_major', type=int, default=65, help='original major version to replace (default 65 for Java 21)')
    parser.add_argument('--to', dest='to_major', type=int, default=61, help='new major version (default 61 for Java 17)')
    parser.add_argument('-r', '--recursive', action='store_true', help='recursively find jars in subdirectories when directories provided')
    parser.add_argument('--out-dir', dest='out_dir', default=None, help='directory to write modified jars into (default: same directory as original)')
    parser.add_argument('--replace', action='store_true', help='replace original jar (but a .bak will be created). By default script writes a new file.')
    parser.add_argument('--dry-run', action='store_true', help='only report which classes would be changed; do not write files')
    parser.add_argument('-v', '--verbose', action='store_true', help='verbose output')
    args = parser.parse_args()

    targets = []
    if args.paths:
        for p in args.paths:
            if os.path.isdir(p):
                targets.extend(list(find_jars(p, recursive=args.recursive)))
            else:
                targets.append(p)
    else:
        targets = list(find_jars('.', recursive=args.recursive))

    if not targets:
        print("No .jar files found to process.")
        sys.exit(0)

    # ensure out_dir exists if provided
    if args.out_dir:
        os.makedirs(args.out_dir, exist_ok=True)

    for jar in targets:
        if not os.path.isfile(jar):
            print(f"Skipping (not a file): {jar}")
            continue
        try:
            process_jar(
                jar,
                from_major=args.from_major,
                to_major=args.to_major,
                out_dir=args.out_dir,
                replace=args.replace,
                dry_run=args.dry_run,
                verbose=args.verbose
            )
        except Exception as e:
            print(f"[!] Error processing {jar}: {e}")

if __name__ == '__main__':
    main()
