<?php
/*
 *                         SWIPE Web Service
 *                Author: Sébastien FAYE [sebastien.faye@uni.lu]
 *
 * ------------------------------------------------------------------------------
 * The MIT License (MIT)
 *
 * Copyright (c) 2015 Sébastien FAYE [sebastien.faye@uni.lu], VehicularLab [vehicular-lab@uni.lu]
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 * ------------------------------------------------------------------------------
 */
 
$isitok = false;

if(isset($_FILES)) {
	$username = $_FILES["uploadedfile"]["name"];
	
	$max_timestamp = 0;
	$dir = opendir("./files");
	while (($file = readdir($dir)) !== false) {
		if(preg_match("#".$username."#i", $file)) {
			$timestamp = intval(preg_replace("#^[^_]+_main_[a-z0-9]+_([0-9]+)$#i", "$1", $file));
			if($max_timestamp < $timestamp) 
				$max_timestamp = $timestamp;
		}
	}
	closedir($dossier);
	
	// Upload data
	$new_timestamp = time();
	$new_file = "./files/".$username."_".$new_timestamp;
	if(move_uploaded_file($_FILES["uploadedfile"]["tmp_name"], $new_file)) {
		$isitok = true;
	}
	
	if($max_timestamp > 0) {
		$cur_file = "./files/".$username."_".$max_timestamp;
		if($new_timestamp - filemtime($cur_file) < 8*60*60) { // We don't need to create a new file if the previous data timestamp < xh
			file_put_contents($cur_file, file_get_contents($new_file), FILE_APPEND);
			@unlink($new_file);
		}
	}
}
echo ($isitok ? "1" : "2");
?>