<?php

// --- CONFIG ---
$supabaseUrl = 'https://yicntsohozgrbhjwccsd.supabase.co';
$supabaseServiceKey = 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InlpY250c29ob3pncmJoandjY3NkIiwicm9sZSI6InNlcnZpY2Vfcm9sZSIsImlhdCI6MTc2MzcyNDA2OSwiZXhwIjoyMDc5MzAwMDY5fQ.xtA92QovqeLSc8qV_Q_V5Bu_9N2UP_yLhVLTey9BHfI';
$bucket = 'images';
// ---------------

header('Content-Type: application/json; charset=utf-8');

// Read incoming JSON
$input = json_decode(file_get_contents('php://input'), true);
if (!isset($input['image_base64'])) {
    echo json_encode(["status" => 0, "error" => ["message" => "Missing image_base64"]]);
    exit;
}

$base64_string = $input['image_base64'];

$imageData = base64_decode($base64_string);
$extension = 'jpg';
$contentType = 'image/jpeg';

if ($imageData === false) {
    echo json_encode(["status" => 0, "error" => ["message" => "Invalid base64 data"]]);
    exit;
}

$filename = 'img_' . uniqid() . '.' . $extension;
$path = $bucket . '/uploads/' . $filename;

// Upload to Supabase Storage
$url = $supabaseUrl . '/storage/v1/object/' . $path;

$ch = curl_init($url);
curl_setopt_array($ch, [
    CURLOPT_CUSTOMREQUEST => 'POST',
    CURLOPT_POSTFIELDS => $imageData,
    CURLOPT_HTTPHEADER => [
        'Authorization: Bearer ' . $supabaseServiceKey,
        'apikey: ' . $supabaseServiceKey,
        'Content-Type: ' . $contentType 
    ],
    CURLOPT_RETURNTRANSFER => true
]);

$response = curl_exec($ch);
$httpCode = curl_getinfo($ch, CURLINFO_HTTP_CODE);
curl_close($ch);

// Return result
if ($httpCode >= 200 && $httpCode < 300) {
    $publicUrl = "$supabaseUrl/storage/v1/object/public/$path";
    echo json_encode([
        "status" => 1,
        "data" => ["url" => $publicUrl]
    ]);
} else {
    echo json_encode([
        "status" => 0,
        "error" => ["message" => "Upload failed", "code" => $httpCode, "details" => $response]
    ]);
}
?>