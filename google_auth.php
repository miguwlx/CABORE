<?php
session_start();
include("conexion.php");
header('Content-Type: application/json');

define('GOOGLE_CLIENT_ID', '853981485167-vsbi9h44ktc32d6c6ppipnls9fc6iqh7.apps.googleusercontent.com');

$credential = $_POST['credential'] ?? '';
if (!$credential) {
    echo json_encode(['ok' => false, 'error' => 'Falta el token de Google.']);
    exit();
}

$verifyUrl = "https://oauth2.googleapis.com/tokeninfo?id_token=" . urlencode($credential);
$respuesta = @file_get_contents($verifyUrl);

if ($respuesta === false) {
    echo json_encode(['ok' => false, 'error' => 'No se pudo verificar el token con Google.']);
    exit();
}

$datos = json_decode($respuesta, true);

if (!$datos || !isset($datos['aud']) || $datos['aud'] !== GOOGLE_CLIENT_ID) {
    echo json_encode(['ok' => false, 'error' => 'Token de Google inválido.']);
    exit();
}

if (($datos['email_verified'] ?? 'false') !== 'true') {
    echo json_encode(['ok' => false, 'error' => 'El correo de Google no está verificado.']);
    exit();
}

$google_id = $datos['sub'];
$correo    = $datos['email'];
$nombre    = $datos['name'] ?? $correo;
$rol       = in_array($_POST['rol'] ?? '', ['cliente', 'emprendedor'], true) ? $_POST['rol'] : 'cliente';

$check = $conn->prepare("SELECT id, nombre, rol, activo, google_id FROM usuarios WHERE correo = ? OR google_id = ?");
$check->bind_param("ss", $correo, $google_id);
$check->execute();
$usuario = $check->get_result()->fetch_assoc();

if ($usuario) {
    if ((int) ($usuario['activo'] ?? 1) === 0) {
        echo json_encode(['ok' => false, 'error' => 'Esta cuenta está suspendida.']);
        exit();
    }
    if (empty($usuario['google_id'])) {
        $upd = $conn->prepare("UPDATE usuarios SET google_id = ? WHERE id = ?");
        $upd->bind_param("si", $google_id, $usuario['id']);
        $upd->execute();
    }
    $usuario_id   = $usuario['id'];
    $nombre_final = $usuario['nombre'];
    $rol_final    = $usuario['rol'];
} else {
    $stmt = $conn->prepare("INSERT INTO usuarios (nombre, correo, contrasena, rol, google_id) VALUES (?, ?, NULL, ?, ?)");
    $stmt->bind_param("ssss", $nombre, $correo, $rol, $google_id);
    if (!$stmt->execute()) {
        echo json_encode(['ok' => false, 'error' => 'No se pudo crear la cuenta.']);
        exit();
    }
    $usuario_id   = $stmt->insert_id;
    $nombre_final = $nombre;
    $rol_final    = $rol;
}

session_regenerate_id(true);
$_SESSION['usuario_id'] = $usuario_id;
$_SESSION['nombre']     = $nombre_final;
$_SESSION['rol']        = $rol_final;

switch ($rol_final) {
    case 'emprendedor':   $redirect = 'emprendedor.php'; break;
    case 'administrador': $redirect = 'admin.php'; break;
    default:              $redirect = 'cliente.php';
}

echo json_encode(['ok' => true, 'redirect' => $redirect]);