<?php
session_start();
include("conexion.php");

$usuario_id = $_SESSION['usuario_id'];

// 🔥 OBTENER TIENDA REAL
$res = $conn->query("SELECT id FROM tiendas WHERE usuario_id = $usuario_id");
$tienda = $res->fetch_assoc();
$tienda_id = $tienda['id'];

// DATOS
$nombre = $_POST['nombre'];
$precio = $_POST['precio'];

// IMAGEN (básico)
$imagen = $_FILES['imagen']['name'];
$ruta = "img/" . $imagen;
move_uploaded_file($_FILES['imagen']['tmp_name'], $ruta);

// INSERTAR BIEN 🔥
$sql = "INSERT INTO productos (nombre, precio, imagen, tienda_id)
        VALUES ('$nombre', '$precio', '$ruta', $tienda_id)";

$conn->query($sql);

header("Location: emprendedor.php");
?>