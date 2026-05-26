<?php
session_start();
include("conexion.php");

if (!isset($_SESSION['carrito'])) {
    $_SESSION['carrito'] = [];
}

if ($_POST['accion'] == 'agregar') {

    $id = $_POST['id'];

    foreach ($_SESSION['carrito'] as &$item) {
        if ($item['id'] == $id) {
            $item['cantidad']++;
            exit();
        }
    }

    $_SESSION['carrito'][] = [
        "id"=>$id,
        "cantidad"=>1
    ];
}

if ($_POST['accion'] == 'mas') {
    $_SESSION['carrito'][$_POST['index']]['cantidad']++;
}

if ($_POST['accion'] == 'menos') {
    $_SESSION['carrito'][$_POST['index']]['cantidad']--;

    if ($_SESSION['carrito'][$_POST['index']]['cantidad'] <= 0) {
        unset($_SESSION['carrito'][$_POST['index']]);
        $_SESSION['carrito'] = array_values($_SESSION['carrito']);
    }
}

if ($_POST['accion'] == 'eliminar') {
    unset($_SESSION['carrito'][$_POST['index']]);
    $_SESSION['carrito'] = array_values($_SESSION['carrito']);
}