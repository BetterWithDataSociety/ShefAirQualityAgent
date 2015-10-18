<?php

if (isset($_REQUEST['id'])) {
   $id = $_REQUEST['id'];
   $url = "http://pastebin.com/raw.php?i=$id";
   $ch = curl_init($url);

   curl_setopt($ch, CURLOPT_POST, true);
   curl_setopt($ch, CURLOPT_POSTFIELDS, $data);  
   curl_setopt($ch, CURLOPT_RETURNTRANSFER, 1);
   curl_setopt($ch, CURLOPT_VERBOSE, 1);
   curl_setopt($ch, CURLOPT_NOBODY, 0);
 
   $response = curl_exec($ch);
   //echo "here it goes\n";
   echo $response;
}
else
{
   echo "no pastebin file id given";
}
exit();
?>