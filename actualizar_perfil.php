<?php
session_start();
require_once 'conexion.php';

// Protección: solo compradores autenticados
if (!isset($_SESSION['usuario_id']) || $_SESSION['rol'] !== 'cliente') {
    header("Location: login.html");
    exit();
}

$usuario_id = (int) $_SESSION['usuario_id'];

$telefono = trim($_POST['telefono'] ?? '');
$ciudad   = trim($_POST['ciudad']   ?? '');
$direccion = trim($_POST['direccion'] ?? '');

// Validación simple del teléfono (solo si escribieron algo)
if ($telefono !== '' && !preg_match('/^[0-9+\-\s()]{7,20}$/', $telefono)) {
    $_SESSION['flash'] = ['tipo' => 'err', 'msg' => 'Revisa el teléfono, parece tener un formato inválido.'];
    header("Location: perfil.php");
    exit();
}

$stmt = $conn->prepare("UPDATE usuarios SET telefono = ?, ciudad = ?, direccion = ? WHERE id = ?");

if (!$stmt) {
    // Si la columna 'direccion' aún no existe en la tabla usuarios, avisamos claramente
    // en vez de mostrar un error genérico o una pantalla en blanco.
    $_SESSION['flash'] = [
        'tipo' => 'err',
        'msg'  => 'No se pudo guardar: falta preparar la base de datos (columna "direccion" en la tabla usuarios). Contacta al administrador del sitio.'
    ];
    header("Location: perfil.php");
    exit();
}

$stmt->bind_param("sssi", $telefono, $ciudad, $direccion, $usuario_id);
$ok = $stmt->execute();

$_SESSION['flash'] = $ok
    ? ['tipo' => 'ok',  'msg' => 'Tus datos se guardaron correctamente.']
    : ['tipo' => 'err', 'msg' => 'No se pudo guardar. Intenta de nuevo.'];

header("Location: perfil.php");
exit();