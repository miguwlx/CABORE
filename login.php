<?php
session_start();
include("conexion.php");

if (!isset($_POST['correo'], $_POST['contrasena'])) {
    header("Location: login.html");
    exit();
}

$correo    = trim($_POST['correo']);
$contrasena = $_POST['contrasena'];

// Prepared statement — evita SQL injection
$stmt = $conn->prepare("SELECT id, nombre, rol, contrasena FROM usuarios WHERE correo = ?");
$stmt->bind_param("s", $correo);
$stmt->execute();
$res = $stmt->get_result();

if ($res->num_rows === 1) {
    $usuario = $res->fetch_assoc();

    // Verificar contraseña (password_hash o comparación directa si aún no usas hash)
    $ok = password_verify($contrasena, $usuario['contrasena'])
        || $usuario['contrasena'] === $contrasena; // fallback para cuentas viejas

    if ($ok) {
        session_regenerate_id(true);
        $_SESSION['usuario_id'] = $usuario['id'];
        $_SESSION['nombre']     = $usuario['nombre'];
        $_SESSION['rol']        = $usuario['rol'];

        switch ($usuario['rol']) {
            case 'emprendedor':
                header("Location: emprendedor.php"); break;
            case 'administrador':
                header("Location: admin.php"); break;
            default:
                header("Location: cliente.php");
        }
        exit();
    }
}

// Credenciales incorrectas
$panel = (isset($_POST['tipo']) && $_POST['tipo'] === 'emprendedor') ? 'empren' : 'cliente';
header("Location: login.html?error=1&panel=" . $panel);
exit();