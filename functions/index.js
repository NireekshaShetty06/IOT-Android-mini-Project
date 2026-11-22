const functions = require("firebase-functions");
const admin = require("firebase-admin");

admin.initializeApp();

/**
 * Cloud Function triggered on any write to /smartUmbrella/status.
 * Detects transition of rainDetected from false to true and sends FCM notifications
 * to all registered device tokens under /smartUmbrella/fcmTokens.
 */
exports.sendRainAlertNotification = functions.database
  .ref("/smartUmbrella/status")
  .onUpdate(async (change, context) => {
    const beforeData = change.before.val();
    const afterData = change.after.val();

    if (!afterData) return null;

    const previouslyRaining = beforeData ? beforeData.rainDetected === true : false;
    const currentlyRaining = afterData.rainDetected === true;

    // Trigger only on genuine state transition from dry to raining
    if (!previouslyRaining && currentlyRaining) {
      console.log("Rain transition detected! Fetching device FCM tokens...");

      const tokensSnapshot = await admin
        .database()
        .ref("/smartUmbrella/fcmTokens")
        .once("value");

      if (!tokensSnapshot.exists()) {
        console.log("No registered FCM tokens found.");
        return null;
      }

      const tokens = Object.keys(tokensSnapshot.val());
      if (tokens.length === 0) return null;

      const payload = {
        notification: {
          title: "Rain Detected!",
          body: "Your Smart Umbrella detected rainfall. Please deploy your canopy!",
        },
        data: {
          eventType: "RAIN_ALERT",
          temperature: String(afterData.temperature || ""),
          humidity: String(afterData.humidity || ""),
          rainAnalogValue: String(afterData.rainAnalogValue || ""),
          timestamp: String(afterData.lastUpdated || Date.now()),
        },
      };

      console.log(`Sending notification to ${tokens.length} devices.`);
      const response = await admin.messaging().sendEachForMulticast({
        tokens: tokens,
        notification: payload.notification,
        data: payload.data,
      });

      // Cleanup invalid tokens
      const tokensToRemove = [];
      response.responses.forEach((result, index) => {
        const error = result.error;
        if (error) {
          console.error("Failure sending notification to", tokens[index], error);
          if (
            error.code === "messaging/invalid-registration-token" ||
            error.code === "messaging/registration-token-not-registered"
          ) {
            tokensToRemove.push(
              admin.database().ref(`/smartUmbrella/fcmTokens/${tokens[index]}`).remove()
            );
          }
        }
      });

      return Promise.all(tokensToRemove);
    }

    return null;
  });
