<?php
session_start();
include("conexion.php");

// Validar acceso
if (!isset($_POST['correo']) || !isset($_POST['contrasena'])) {
    echo "Acceso no permitido";
    exit();
}

$correo = $conn->real_escape_string($_POST['correo']);
$contrasena = $conn->real_escape_string($_POST['contrasena']);

$sql = "SELECT * FROM usuarios 
        WHERE correo='$correo' 
        AND contraseña='$contrasena'";

$resultado = $conn->query($sql);

if ($resultado->num_rows > 0) {

    $usuario = $resultado->fetch_assoc();

    $_SESSION['usuario_id'] = $usuario['id'];
    $_SESSION['rol'] = $usuario['rol'];

    if ($usuario['rol'] == "emprendedor") {
        header("Location: emprendedor.php");
        exit();
    } else if ($usuario['rol'] == "cliente") {
        header("Location: cliente.php");
        exit();
    } else if ($usuario['rol'] == "administrador") {
        header("Location: emprendedor.php");
        exit();
    } else {
        header("Location: index.html");
        exit();
    }

} else {
    echo "<p style='color:red; font-family:Segoe UI; text-align:center; margin-top:30px;'>
        Usuario o contraseña incorrectos. 
        <a href='index.html'>Volver</a>
    </p>";
}
?>
