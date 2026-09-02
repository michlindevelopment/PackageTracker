# PluginActivity and TrackingSmsProvider are declared in AndroidManifest.xml,
# and AGP generates keep rules for manifest-declared components, so neither
# needs an explicit -keep here.
#
# Nothing else in this APK is reached reflectively: the provider's contract is
# plain strings (column names and a query parameter), which R8 does not touch,
# and the main app addresses it by authority rather than by class name.
