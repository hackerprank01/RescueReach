const functions = require("firebase-functions");
const admin = require("firebase-admin");
const twilio = require("twilio");

// Initialize Firebase Admin SDK
admin.initializeApp();

// Twilio configuration
const accountSid = functions.config().twilio.account_sid;
const authToken = functions.config().twilio.auth_token;
const twilioPhoneNumber = functions.config().twilio.phone_number;
const twilioClient = new twilio(accountSid, authToken);

/**
 * Cloud Function to notify responders via FCM when an SOS is reported
 */
exports.notifyEmergencyResponders = functions.https.onCall(
  async (data, context) => {
    // Check authentication
    if (!context.auth) {
      throw new functions.https.HttpsError(
        "unauthenticated",
        "User must be authenticated"
      );
    }

    console.log("Notifying responders for emergency:", data);

    try {
      const reportId = data.reportId;
      const emergencyType = data.emergencyType;
      const latitude = data.latitude;
      const longitude = data.longitude;
      const address = data.address || "Unknown address";
      const city = data.city || "";
      const state = data.state || "";

      // Query for responders based on emergency type and location
      // This is a basic implementation - in production you would use GeoFirestore
      let respondersQuery = admin.firestore().collection("responders");

      // Filter by emergency type if specified
      if (emergencyType) {
        respondersQuery = respondersQuery.where(
          "services",
          "array-contains",
          emergencyType
        );
      }

      // Filter by state if specified
      if (state && state.trim() !== "") {
        respondersQuery = respondersQuery.where("state", "==", state);
      }

      const respondersSnapshot = await respondersQuery.get();

      if (respondersSnapshot.empty) {
        console.log("No matching responders found");
        return {
          success: false,
          notifiedCount: 0,
          message: "No matching responders found",
        };
      }

      const notificationPromises = [];
      let notifiedCount = 0;

      // Send FCM notifications to responders
      respondersSnapshot.forEach((doc) => {
        const responder = doc.data();

        // Skip responders without FCM tokens
        if (!responder.fcmToken) {
          console.log("Skipping responder without FCM token:", doc.id);
          return;
        }

        console.log("Sending notification to responder:", doc.id);

        const notification = {
          notification: {
            title: `${emergencyType} Emergency Alert`,
            body: `Emergency at ${address}, ${city}, ${state}. Tap to respond.`,
          },
          data: {
            reportId: reportId,
            emergencyType: emergencyType,
            latitude: String(latitude),
            longitude: String(longitude),
            address: address,
            city: city,
            state: state,
            clickAction: "OPEN_EMERGENCY_DETAILS",
          },
          token: responder.fcmToken,
        };

        notificationPromises.push(
          admin
            .messaging()
            .send(notification)
            .then(() => {
              notifiedCount++;
              console.log("Notification sent to responder:", doc.id);
            })
            .catch((error) => {
              console.error(
                "Error sending notification to responder:",
                doc.id,
                error
              );
            })
        );
      });

      await Promise.all(notificationPromises);

      console.log(`Successfully notified ${notifiedCount} responders`);
      return { success: true, notifiedCount: notifiedCount };
    } catch (error) {
      console.error("Error notifying responders:", error);
      throw new functions.https.HttpsError(
        "internal",
        "Error notifying responders",
        error
      );
    }
  }
);

/**
 * Cloud Function to send emergency SMS via Twilio
 */
exports.sendEmergencySMS = functions.https.onCall(async (data, context) => {
  // Check authentication
  if (!context.auth) {
    throw new functions.https.HttpsError(
      "unauthenticated",
      "User must be authenticated"
    );
  }

  console.log("Sending emergency SMS:", data);

  try {
    const reportId = data.reportId;
    const phoneNumbers = data.phoneNumbers;
    const emergencyType = data.emergencyType;
    const userName = data.userName || "A person";
    const address = data.address;
    const city = data.city || "";
    const state = data.state || "";
    const nearestServiceName = data.nearestServiceName || "";
    const nearestServicePhone = data.nearestServicePhone || "";
    const nearestServiceDistance = data.nearestServiceDistance || "";

    if (!phoneNumbers || !phoneNumbers.length) {
      throw new functions.https.HttpsError(
        "invalid-argument",
        "No phone numbers provided"
      );
    }

    // Format the message
    const message = `EMERGENCY ALERT: ${userName} has reported a ${emergencyType} emergency.

Location: ${address}, ${city}, ${state}

${
  nearestServiceName
    ? `Nearest ${emergencyType}: ${nearestServiceName} (${nearestServiceDistance})`
    : ""
}
${nearestServicePhone ? `Emergency service #: ${nearestServicePhone}` : ""}

SOS ID: ${reportId} for status tracking.

This is an automated emergency alert. Please contact emergency services if needed.`;

    console.log("SMS message:", message);
    console.log("Sending to numbers:", phoneNumbers);

    const smsPromises = [];
    const successfulNumbers = [];
    const failedNumbers = {};

    // Send SMS to each emergency contact
    for (const phoneNumber of phoneNumbers) {
      console.log(`Sending SMS to ${phoneNumber}`);

      smsPromises.push(
        twilioClient.messages
          .create({
            body: message,
            from: twilioPhoneNumber,
            to: phoneNumber,
          })
          .then((message) => {
            console.log(`SMS sent to ${phoneNumber}, SID: ${message.sid}`);
            successfulNumbers.push(phoneNumber);
          })
          .catch((error) => {
            console.error(`Error sending SMS to ${phoneNumber}:`, error);
            failedNumbers[phoneNumber] = error.message;
          })
      );
    }

    // Wait for all SMS operations to complete
    await Promise.all(smsPromises);

    // Update the report with SMS status
    if (reportId) {
      await admin
        .firestore()
        .collection("sos_reports")
        .doc(reportId)
        .update({
          smsSent: successfulNumbers.length > 0,
          smsStatus: successfulNumbers.length > 0 ? "SENT" : "FAILED",
          smsDetails: {
            successfulNumbers,
            failedNumbers,
            timestamp: admin.firestore.FieldValue.serverTimestamp(),
          },
        });
    }

    console.log(`Successfully sent SMS to ${successfulNumbers.length} numbers`);
    return {
      success: successfulNumbers.length > 0,
      messagesSent: successfulNumbers.length,
      successfulNumbers,
      failedNumbers,
    };
  } catch (error) {
    console.error("Error sending emergency SMS:", error);
    throw new functions.https.HttpsError(
      "internal",
      "Error sending emergency SMS",
      error
    );
  }
});

/**
 * Firestore trigger to send FCM to the user when SOS status changes
 */
exports.notifyUserOnSOSStatusChange = functions.firestore.onDocumentUpdated(
  "sos_reports/{reportId}",
  async (event) => {
    const reportId = event.params.reportId;
    const newData = event.data.after.data();
    const previousData = event.data.before.data();

    // Only continue if status has changed
    if (newData.status === previousData.status) {
      console.log("Status unchanged, skipping notification");
      return null;
    }

    console.log(
      `Status changed from ${previousData.status} to ${newData.status} for report ${reportId}`
    );

    // Get user's FCM token from users collection
    const userPhone = newData.userPhoneNumber;
    if (!userPhone) {
      console.log("No user phone number associated with report");
      return null;
    }

    // Look up user document to get FCM token
    const userDoc = await admin
      .firestore()
      .collection("users")
      .doc(userPhone)
      .get();

    if (!userDoc.exists) {
      console.log(`User document not found for ${userPhone}`);
      return null;
    }

    const userData = userDoc.data();
    const fcmToken = userData.fcmToken;

    if (!fcmToken) {
      console.log(`No FCM token found for user ${userPhone}`);
      return null;
    }

    // Create notification based on new status
    const title = "SOS Status Update";
    let body = `Your SOS report status has changed to ${newData.status}`;

    switch (newData.status) {
      case "RECEIVED":
        body = "Your emergency has been received by our system";
        break;
      case "RESPONDING":
        body = "Help is on the way to your location";
        break;
      case "RESOLVED":
        body = "Your emergency has been marked as resolved";
        break;
    }

    // Send FCM notification to user
    try {
      await admin.messaging().send({
        notification: {
          title: title,
          body: body,
        },
        data: {
          reportId: reportId,
          status: newData.status,
          clickAction: "OPEN_SOS_DETAILS",
        },
        token: fcmToken,
      });

      console.log(
        `Notification sent to user ${userPhone} for report ${reportId}`
      );

      // Also update the report to indicate notification was sent
      await event.data.after.ref.update({
        notificationSent: true,
        lastNotification: {
          status: newData.status,
          timestamp: admin.firestore.FieldValue.serverTimestamp(),
        },
      });

      return true;
    } catch (error) {
      console.error("Error sending notification to user:", error);
      return null;
    }
  }
);

/**
 * Firestore trigger to update the realtime database when report status changes
 * This ensures both databases stay in sync
 */
exports.syncSOSStatusToRealtimeDB = functions.firestore.onDocumentUpdated(
  "sos_reports/{reportId}",
  async (event) => {
    const reportId = event.params.reportId;
    const newData = event.data.after.data();
    const previousData = event.data.before.data();

    // Only continue if status has changed
    if (newData.status === previousData.status) {
      return null;
    }

    try {
      // Update realtime database
      await admin
        .database()
        .ref(`active_sos/${reportId}/status`)
        .set(newData.status);

      // If status is RESOLVED, move to archived_sos after a delay
      if (newData.status === "RESOLVED") {
        await admin
          .database()
          .ref(`active_sos/${reportId}`)
          .once("value", (snapshot) => {
            const sosData = snapshot.val();
            if (sosData) {
              // Add to archived_sos
              admin
                .database()
                .ref(`archived_sos/${reportId}`)
                .set({
                  ...sosData,
                  status: "RESOLVED",
                  resolvedAt: admin.database.ServerValue.TIMESTAMP,
                });

              // Remove from active_sos after a delay (5 minutes)
              setTimeout(() => {
                admin.database().ref(`active_sos/${reportId}`).remove();
              }, 5 * 60 * 1000);
            }
          });
      }

      return null;
    } catch (error) {
      console.error("Error syncing status to realtime database:", error);
      return null;
    }
  }
);
