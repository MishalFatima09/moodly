<?php
require_once 'conn.php';

//returns 
// - status (1,0)
// - data (on success) 
// - error message (on error)

// data can be of two types:
// - for SELECT or INSERT/UPDATE/DELETE with RETURNING: array of rows
// - for other queries: number of affected rows
function json_response($status, $data, $errorMessage, $http_code) {
    http_response_code($http_code);
    header('Content-Type: application/json; charset=utf-8');

    $response = ['status' => $status];

    if ($status === 1) {
        $response['data'] = $data;
    } else {
        $response['error'] = ['message' => $errorMessage];
    }

    echo json_encode($response);
    exit;
}

// --- SCRIPT LOGIC ---

// Only accept POST
if ($_SERVER['REQUEST_METHOD'] !== 'POST') {
    json_response(0, null, 'Use POST', 405);
}

// Get the raw POST body and decode it
$json_body = file_get_contents('php://input');
$data = json_decode($json_body, true);

if (json_last_error() !== JSON_ERROR_NONE) {
    json_response(0, null, 'Invalid JSON body', 400);
}

// Get the query template and the parameters
$query = $data['query'] ?? '';
$params = $data['params'] ?? [];

if (trim($query) === '') {
    json_response(0, null, 'No query provided', 400);
}

try {
    // 1. Prepare and execute
    $stmt = $pdo->prepare($query);
    $stmt->execute($params);

    $payload = [];

    // 2. Check if the query returned columns
    if ($stmt->columnCount() > 0) {
        // For SELECT or RETURNING
        $payload = [
            'type' => 'select_or_returning',
            'rows' => $stmt->fetchAll(PDO::FETCH_ASSOC)
        ];
    } else {
        // For INSERT/UPDATE/DELETE without RETURNING
        $payload = [
            'type' => 'other',
            'affected_rows' => $stmt->rowCount()
        ];
    }
    
    // 3. Send successful response
    json_response(1, $payload, null, 200);

} catch (PDOException $e) {
    // Send database error response
    json_response(0, null, $e->getMessage(), 500);
}