#!/usr/bin/env python3
"""Compile a generated ROM2APK Android source project and sign its APK.

The input is a ZIP created by AndroidPortProjectGenerator, not a ROM archive.
Signing credentials are read only from environment variables.
"""
from __future__ import annotations

import argparse
import os
import pathlib
import shutil
import subprocess
import sys
import tempfile
import zipfile


MAX_ENTRIES = 5000
MAX_UNCOMPRESSED = 512 * 1024 * 1024


def safe_extract(archive: pathlib.Path, destination: pathlib.Path) -> None:
    with zipfile.ZipFile(archive) as zf:
        entries = zf.infolist()
        if len(entries) > MAX_ENTRIES:
            raise ValueError("El proyecto contiene demasiados archivos.")
        total = sum(entry.file_size for entry in entries)
        if total > MAX_UNCOMPRESSED:
            raise ValueError("El proyecto supera el límite de extracción de 512 MiB.")
        seen: set[str] = set()
        for entry in entries:
            name = entry.filename
            if "\\\\" in name or "\\x00" in name:
                raise ValueError(f"Ruta insegura en ZIP: {entry.filename}")
            path = pathlib.PurePosixPath(name)
            if path.is_absolute() or not path.parts or any(p in ("", ".", "..") for p in path.parts):
                raise ValueError(f"Ruta insegura en ZIP: {entry.filename}")
            if ":" in path.parts[0] or path.as_posix() + ("/" if entry.is_dir() else "") != name:
                raise ValueError(f"Ruta no canónica en ZIP: {entry.filename}")
            canonical_target = path.as_posix()
            if canonical_target in seen:
                raise ValueError(f"Entrada duplicada o colisión de ruta en ZIP: {name}")
            seen.add(canonical_target)
            # Reject Unix symlinks; generated projects only need regular files/directories.
            mode = (entry.external_attr >> 16) & 0o170000
            if mode == 0o120000:
                raise ValueError(f"No se permiten enlaces simbólicos: {name}")
            target = destination.joinpath(*path.parts)
            target.parent.mkdir(parents=True, exist_ok=True)
            if entry.is_dir():
                target.mkdir(parents=True, exist_ok=True)
            else:
                with zf.open(entry) as src, target.open("wb") as dst:
                    shutil.copyfileobj(src, dst)


def run(command: list[str], cwd: pathlib.Path | None = None) -> None:
    print("+", " ".join(command), flush=True)
    subprocess.run(command, cwd=cwd, check=True)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("project_zip", type=pathlib.Path, help="ZIP del proyecto Android fuente generado por ROM2APK")
    parser.add_argument("--output", type=pathlib.Path, default=pathlib.Path("ported-game-signed.apk"))
    parser.add_argument("--keep-project", type=pathlib.Path, help="Conservar el proyecto extraído en esta carpeta")
    args = parser.parse_args()

    sdk = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
    keystore = os.environ.get("ROM2APK_KEYSTORE")
    alias = os.environ.get("ROM2APK_KEY_ALIAS")
    store_password = os.environ.get("ROM2APK_STORE_PASSWORD")
    key_password = os.environ.get("ROM2APK_KEY_PASSWORD")
    missing = [name for name, value in (
        ("ANDROID_HOME/ANDROID_SDK_ROOT", sdk),
        ("ROM2APK_KEYSTORE", keystore),
        ("ROM2APK_KEY_ALIAS", alias),
        ("ROM2APK_STORE_PASSWORD", store_password),
        ("ROM2APK_KEY_PASSWORD", key_password),
    ) if not value]
    if missing:
        parser.error("Faltan variables de entorno: " + ", ".join(missing))

    archive = args.project_zip.resolve()
    if not archive.is_file():
        parser.error(f"No existe el ZIP: {archive}")
    keystore_path = pathlib.Path(keystore).expanduser().resolve()
    if not keystore_path.is_file():
        parser.error(f"No existe el keystore: {keystore_path}")

    temporary = tempfile.TemporaryDirectory(prefix="rom2apk-build-")
    project = pathlib.Path(temporary.name) / "project"
    project.mkdir()
    try:
        safe_extract(archive, project)
        required = [
            project / "settings.gradle.kts",
            project / "build.gradle.kts",
            project / "app" / "build.gradle.kts",
            project / "app" / "src" / "main" / "AndroidManifest.xml",
            project / "app" / "src" / "main" / "java" / "generated" / "port" / "GeneratedPortActivity.kt",
            project / "app" / "src" / "main" / "java" / "generated" / "port" / "VirtualControlsView.kt",
        ]
        absent = [str(path.relative_to(project)) for path in required if not path.is_file()]
        if absent:
            raise ValueError("El ZIP no parece un proyecto ROM2APK válido; faltan: " + ", ".join(absent))
        if not any(project.glob("app/src/main/java/**/GeneratedGameImplementation.kt")):
            raise ValueError("No hay GeneratedGameImplementation.kt: el backend aún no ha generado el port jugable.")

        if args.keep_project:
            if args.keep_project.exists():
                raise ValueError(f"La carpeta de destino ya existe: {args.keep_project}")
            shutil.copytree(project, args.keep_project)
            project = args.keep_project.resolve()

        gradle = shutil.which("gradle") or shutil.which("gradle.bat")
        if not gradle:
            raise RuntimeError("No se encuentra Gradle en PATH.")
        run([gradle, "--no-daemon", ":app:assembleRelease"], cwd=project)

        unsigned = project / "app/build/outputs/apk/release/app-release-unsigned.apk"
        if not unsigned.is_file():
            raise RuntimeError("Gradle terminó sin producir app-release-unsigned.apk.")

        sdk_path = pathlib.Path(sdk)
        build_tools = sorted(
            (p for p in (sdk_path / "build-tools").iterdir() if p.is_dir()),
            key=lambda p: tuple(int(x) for x in p.name.split(".") if x.isdigit()),
        ) if (sdk_path / "build-tools").is_dir() else []
        tools_dir = next(
            (directory for directory in reversed(build_tools)
             if (directory / "apksigner").is_file() and (directory / "zipalign").is_file()),
            None,
        )
        if tools_dir is None:
            raise RuntimeError("No se encontraron apksigner y zipalign en Android SDK build-tools.")
        apksigner = str(tools_dir / "apksigner")
        zipalign = str(tools_dir / "zipalign")
        aligned = project / "app" / "build" / "outputs" / "apk" / "release" / "app-release-aligned.apk"
        run([zipalign, "-f", "-p", "4", str(unsigned), str(aligned)])
        output = args.output.resolve()
        output.parent.mkdir(parents=True, exist_ok=True)
        run([
            apksigner, "sign", "--ks", str(keystore_path),
            "--ks-key-alias", alias,
            "--ks-pass", "env:ROM2APK_STORE_PASSWORD",
            "--key-pass", "env:ROM2APK_KEY_PASSWORD",
            "--out", str(output), str(aligned),
        ])
        run([apksigner, "verify", "--verbose", "--print-certs", str(output)])
        run([zipalign, "-c", "-v", "4", str(output)])
        print(f"APK firmado y verificado: {output}")
        return 0
    finally:
        temporary.cleanup()


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (ValueError, RuntimeError, subprocess.CalledProcessError, zipfile.BadZipFile) as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        raise SystemExit(1)
