<!DOCTYPE html>
<html>
<head><meta charset="utf-8"><title>Key search results</title></head>
<body>
<h1>Search results: ${keyCount} key(s)</h1>
<table border="1">
<tr>
  <th>Fingerprint</th>
  <th>Algorithm (OpenPGP code)</th>
  <th>Created</th>
  <th>Expires</th>
  <th>Flags</th>
  <th>UIDs</th>
</tr>
<#list keys as key>
<tr>
  <td>${key.fingerprint}</td>
  <td>${key.algorithm}</td>
  <td>${key.creationTime}</td>
  <td>${key.expirationTime}</td>
  <td>${key.flags}</td>
  <td><#list key.uids as uid>${uid.uidRaw}<br></#list></td>
</tr>
</#list>
</table>
</body>
</html>
