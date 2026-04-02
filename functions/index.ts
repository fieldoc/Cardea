import * as functions from "firebase-functions";
import * as admin from "firebase-admin";

admin.initializeApp();

export const onPartnerRun = functions.database
  .ref("/users/{userId}/activity/lastRunDate")
  .onWrite(async (change, context) => {
    const newDate = change.after.val();
    if (!newDate) return;

    const userId = context.params.userId;
    const userSnap = await admin.database().ref(`/users/${userId}`).once("value");
    const userData = userSnap.val();
    if (!userData) return;

    const partnerIds = Object.keys(userData.partners || {});
    if (partnerIds.length === 0) return;

    const displayName = userData.displayName || "Your partner";

    const sendPromises = partnerIds.map(async (partnerId) => {
      const partnerSnap = await admin.database().ref(`/users/${partnerId}`).once("value");
      const partnerData = partnerSnap.val();
      if (!partnerData?.fcmToken) return;

      return admin.messaging().send({
        token: partnerData.fcmToken,
        notification: {
          title: `${displayName} just finished a run`,
          body: "Your turn? 💪",
        },
        android: {
          notification: {
            channelId: "partner_activity",
            icon: "ic_notification",
          },
        },
      });
    });

    await Promise.allSettled(sendPromises);
  });
