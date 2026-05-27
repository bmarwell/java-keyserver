<#-- Machine-readable HKP index response (options=mr). -->
<#-- Plain text output (.ftl); all values must be pre-processed by Java before being passed here. -->
<#-- Template variables: -->
<#--   keyCount  (int)    - total number of keys in the result set -->
<#--   keys      (list)   - each entry is a map with fields: -->
<#--     fingerprint   (String) -->
<#--     algorithm     (int)    - raw OpenPGP algorithm code -->
<#--     bitStrength   (String) - empty string for ECC, numeric string for RSA/DSA -->
<#--     ctimeEpoch    (String) - epoch seconds of creation time -->
<#--     exptimeEpoch  (String) - epoch seconds of expiration, or empty string -->
<#--     flags         (String) - computed flag chars: r/d/e combination -->
<#--     uids          (list)   - each entry is a map with fields: -->
<#--       encodedUid    (String) - percent-encoded UID string (RFC 3986, %20 for space) -->
<#--       ctimeEpoch    (String) -->
<#--       exptimeEpoch  (String) -->
<#--       flags         (String) -->
info:1:${keyCount}
<#list keys as key>
pub:${key.fingerprint}:${key.algorithm}:${key.bitStrength}:${key.ctimeEpoch}:${key.exptimeEpoch}:${key.flags}
<#list key.uids as uid>
uid:${uid.encodedUid}:${uid.ctimeEpoch}:${uid.exptimeEpoch}:${uid.flags}
</#list>
</#list>
