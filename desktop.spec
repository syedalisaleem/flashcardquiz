# -*- mode: python ; coding: utf-8 -*-
# Build: pyinstaller desktop.spec

from PyInstaller.utils.hooks import collect_data_files

a = Analysis(
    ["desktop.py"],
    pathex=[],
    binaries=[],
    datas=[("static", "static"), ("app", "app")] + collect_data_files("rapidocr_onnxruntime"),
    hiddenimports=[
        "app.main",
        "uvicorn.logging",
        "uvicorn.loops",
        "uvicorn.loops.auto",
        "uvicorn.protocols",
        "uvicorn.protocols.http",
        "uvicorn.protocols.http.auto",
        "uvicorn.protocols.websockets",
        "uvicorn.protocols.websockets.auto",
        "uvicorn.lifespan",
        "uvicorn.lifespan.on",
        "uvicorn.lifespan.off",
        "webview",
        "webview.platforms",
        "webview.platforms.edgechromium",
        "pypdfium2",
        "rapidocr_onnxruntime",
        "onnxruntime",
        "cv2",
        "pyclipper",
        "shapely",
    ],
    hookspath=[],
    hooksconfig={},
    runtime_hooks=[],
    excludes=[
        "torch",
        "torchvision",
        "tensorflow",
        "onnxruntime.transformers",
        "matplotlib",
        "networkx",
        "sympy",
        "dill",
        "pandas",
        "tensorboard",
    ],
    noarchive=False,
)
pyz = PYZ(a.pure)

exe = EXE(
    pyz,
    a.scripts,
    a.binaries,
    a.datas,
    [],
    name="FlashcardQuizApp",
    debug=False,
    bootloader_ignore_signals=False,
    strip=False,
    upx=False,
    console=False,
    disable_windowed_traceback=False,
)
