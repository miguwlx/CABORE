<?php
session_start();
include("conexion.php");

header('Content-Type: application/json');

if (!isset($_SESSION['carrito'])) {
    $_SESSION['carrito'] = [];
}

$accion = $_POST['accion'] ?? '';

switch ($accion) {

    case 'agregar':
        $id = (int)($_POST['id'] ?? 0);
        if ($id <= 0) break;

        // Verificar que el producto existe
        $res = $conn->query("SELECT id FROM productos WHERE id=$id");
        if ($res->num_rows === 0) break;

        foreach ($_SESSION['carrito'] as &$item) {
            if ((int)$item['id'] === $id) {
                $item['cantidad']++;
                echo json_encode(['ok'=>true,'accion'=>'incrementado']);
                exit();
            }
        }
        $_SESSION['carrito'][] = ['id' => $id, 'cantidad' => 1];
        echo json_encode(['ok'=>true,'accion'=>'agregado']);
        break;

    case 'mas':
        $idx = (int)($_POST['index'] ?? -1);
        if (isset($_SESSION['carrito'][$idx])) {
            $_SESSION['carrito'][$idx]['cantidad']++;
        }
        echo json_encode(['ok'=>true]);
        break;

    case 'menos':
        $idx = (int)($_POST['index'] ?? -1);
        if (isset($_SESSION['carrito'][$idx])) {
            $_SESSION['carrito'][$idx]['cantidad']--;
            if ($_SESSION['carrito'][$idx]['cantidad'] <= 0) {
                array_splice($_SESSION['carrito'], $idx, 1);
            }
        }
        echo json_encode(['ok'=>true]);
        break;

    case 'eliminar':
        $idx = (int)($_POST['index'] ?? -1);
        if (isset($_SESSION['carrito'][$idx])) {
            array_splice($_SESSION['carrito'], $idx, 1);
        }
        echo json_encode(['ok'=>true]);
        break;

    case 'vaciar':
        $_SESSION['carrito'] = [];
        echo json_encode(['ok'=>true]);
        break;

    default:
        echo json_encode(['ok'=>false,'error'=>'Acción no reconocida']);
}
