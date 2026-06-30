<?php
session_start();
include("conexion.php");

if (!isset($_SESSION['usuario_id']) || $_SESSION['rol'] !== 'emprendedor') {
    header("Location: login.html"); exit();
}

$usuario_id   = (int)$_SESSION['usuario_id'];
$nombre       = trim($_POST['nombre']       ?? '');
$precio       = (float)($_POST['precio']    ?? 0);
$precio_oferta = !empty($_POST['precio_oferta']) ? (float)$_POST['precio_oferta'] : null;
$descripcion  = trim($_POST['descripcion']  ?? '');
$categoria_id = (int)($_POST['categoria_id'] ?? 12);
$stock        = max(0, (int)($_POST['stock'] ?? 0));
$destacado    = isset($_POST['destacado'])  ? 1 : 0;
$activo       = isset($_POST['activo'])     ? 1 : 0;

// Validar
if (empty($nombre) || $precio <= 0) {
    $_SESSION['flash'] = ['tipo' => 'err', 'msg' => 'El nombre y precio son obligatorios.'];
    header("Location: emprendedor.php"); exit();
}

// Obtener tienda del emprendedor
$stmtT = $conn->prepare("SELECT id FROM tiendas WHERE usuario_id = ?");
$stmtT->bind_param("i", $usuario_id);
$stmtT->execute();
$tienda    = $stmtT->get_result()->fetch_assoc();
$tienda_id = (int)$tienda['id'];

// Manejar imagen
$ruta = null;
if (isset($_FILES['imagen']) && $_FILES['imagen']['error'] === UPLOAD_ERR_OK) {
    $ext     = strtolower(pathinfo($_FILES['imagen']['name'], PATHINFO_EXTENSION));
    $allowed = ['jpg','jpeg','png','webp','gif'];

    if (in_array($ext, $allowed) && $_FILES['imagen']['size'] <= 5 * 1024 * 1024) {
        $dir = "img/productos/";
        if (!is_dir($dir)) mkdir($dir, 0755, true);
        $filename = "prod_" . uniqid() . "." . $ext;
        if (move_uploaded_file($_FILES['imagen']['tmp_name'], $dir . $filename)) {
            $ruta = $dir . $filename;
        }
    }
}

$stmt = $conn->prepare(
    "INSERT INTO productos
        (tienda_id, categoria_id, nombre, descripcion, precio, precio_oferta, imagen, stock, destacado, activo)
     VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
);
$stmt->bind_param(
    "iissddsiii",
    $tienda_id, $categoria_id, $nombre, $descripcion,
    $precio, $precio_oferta, $ruta, $stock, $destacado, $activo
);
$ok = $stmt->execute();

$_SESSION['flash'] = $ok
    ? ['tipo' => 'ok',  'msg' => "Producto «{$nombre}» guardado correctamente."]
    : ['tipo' => 'err', 'msg' => 'Error al guardar el producto. Intenta de nuevo.'];

header("Location: emprendedor.php");
exit();