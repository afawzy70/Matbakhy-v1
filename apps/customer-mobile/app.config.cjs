// Expo reads this file on top of app.json. APP_VARIANT selects the build identity:
//   (unset)   development  = app.json as it is (com.matbakhy.customer.dev, scheme matbakhy-dev), used by the native CI and emulators
//   preview   iOS/Android beta against the Contabo Preview API and Keycloak (EAS profile "testflight"): its own bundle identifier,
//             scheme and display name, so it installs next to a development build and its OIDC redirect URI is registered
//             separately in Keycloak (matbakhy-beta://auth/callback). No secret is ever read or written here.
// Production identifiers and signing remain owner decision O08; this variant is not a production identity.
const PREVIEW = {
  name: "Matbakhy Beta",
  scheme: "matbakhy-beta",
  iosBundleIdentifier: "com.matbakhy.customer.beta",
  androidPackage: "com.matbakhy.customer.beta",
  version: "0.1.0",
};

module.exports = ({ config }) => {
  const variant = process.env.APP_VARIANT || "development";
  if (variant === "development") {
    return config;
  }
  if (variant !== "preview") {
    throw new Error(`Unknown APP_VARIANT "${variant}" (expected development or preview)`);
  }
  return {
    ...config,
    name: PREVIEW.name,
    version: PREVIEW.version,
    scheme: PREVIEW.scheme,
    ios: {
      ...config.ios,
      bundleIdentifier: PREVIEW.iosBundleIdentifier,
      // EAS owns the build number (eas.json: appVersionSource remote + autoIncrement); this value only seeds the first build.
      buildNumber: "1",
      infoPlist: {
        ...(config.ios?.infoPlist ?? {}),
        // The app only uses HTTPS (exempt encryption). Confirm this answer with the owner before the first upload.
        ITSAppUsesNonExemptEncryption: false,
      },
    },
    android: { ...config.android, package: PREVIEW.androidPackage },
    extra: { ...config.extra, environment: "staging", notificationPrompt: false, note: "Preview beta against the Contabo Preview environment. Not a production identity (O08)." },
  };
};
