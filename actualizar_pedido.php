<?php
/**
 * actualizar_pedido.php — Permite al emprendedor cambiar el estado de un
 * pedido (pendiente, confirmado, enviado, entregado, cancelado) desde la
 * pestaña "Ventas" de su panel.
 *
 * Seguridad: solo se permite si el pedido contiene al menos un producto de
 * la tienda del emprendedor autenticado (nunca se confía en el pedido_id
 * "a ciegas").
 */
session_start();
include("conexion.php");

header('Content-Type: application/json');

if (!isset($_SESSION['usuario_id']) || $_SESSION['rol'] !== 'emprendedor') {
    http_response_code(401);
    echo json_encode(['ok' => false, 'error' => 'Debes iniciar sesión como emprendedor.']);
    exit();
}

$usuario_id = (int) $_SESSION['usuario_id'];
$pedido_id  = (int) ($_POST['pedido_id'] ?? 0);
$estado     = $_POST['estado'] ?? '';

$estados_validos = ['pendiente', 'confirmado', 'enviado', 'entregado', 'cancelado'];

if ($pedido_id <= 0 || !in_array($estado, $estados_validos, true)) {
    echo json_encode(['ok' => false, 'error' => 'Datos inválidos.']);
    exit();
}

// Verificar que el emprendedor tenga una tienda
$stmtT = $conn->prepare("SELECT id FROM tiendas WHERE usuario_id = ?");
$stmtT->bind_param("i", $usuario_id);
$stmtT->execute();
$tienda = $stmtT->get_result()->fetch_assoc();

if (!$tienda) {
    echo json_encode(['ok' => false, 'error' => 'No tienes una tienda asociada.']);
    exit();
}
$tienda_id = (int) $tienda['id'];

// Verificar que el pedido contenga al menos un producto de esta tienda
$stmtChk = $conn->prepare("
    SELECT COUNT(*) AS n
    FROM pedido_items pi
    JOIN productos p ON p.id = pi.producto_id
    WHERE pi.pedido_id = ? AND p.tienda_id = ?
");
$stmtChk->bind_param("ii", $pedido_id, $tienda_id);
$stmtChk->execute();
$row = $stmtChk->get_result()->fetch_assoc();

if (!$row || $row['n'] == 0) {
    http_response_code(403);
    echo json_encode(['ok' => false, 'error' => 'Este pedido no pertenece a tu tienda.']);
    exit();
}

$stmtUp = $conn->prepare("UPDATE pedidos SET estado = ? WHERE id = ?");
$stmtUp->bind_param("si", $estado, $pedido_id);
$ok = $stmtUp->execute();

echo json_encode($ok
    ? ['ok' => true]
    : ['ok' => false, 'error' => 'No se pudo actualizar el pedido.']
);