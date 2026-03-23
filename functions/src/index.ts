import * as admin from "firebase-admin";
import { onCall, HttpsError } from "firebase-functions/v2/https";
import { onDocumentCreated } from "firebase-functions/v2/firestore";
import { onSchedule } from "firebase-functions/v2/scheduler";

admin.initializeApp();
const db = admin.firestore();

// --- createInvite ---
export const createInvite = onCall(async (request) => {
  const uid = request.data.uid;
  if (!uid) throw new HttpsError("invalid-argument", "uid required");

  const chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
  for (let attempt = 0; attempt < 3; attempt++) {
    let code = "";
    for (let i = 0; i < 6; i++) {
      code += chars.charAt(Math.floor(Math.random() * chars.length));
    }
    const docRef = db.collection("pairings").doc(code);
    const existing = await docRef.get();
    if (!existing.exists) {
      await docRef.set({
        creatorId: uid,
        createdAt: admin.firestore.FieldValue.serverTimestamp(),
        expiresAt: new Date(Date.now() + 24 * 60 * 60 * 1000),
      });
      return { code };
    }
  }
  throw new HttpsError("unavailable", "Could not generate unique code");
});

// --- acceptInvite ---
export const acceptInvite = onCall(async (request) => {
  const { uid, code } = request.data;
  if (!uid || !code) throw new HttpsError("invalid-argument", "uid and code required");

  const pairingRef = db.collection("pairings").doc(code);

  // Read pairing data before transaction (for FCM after)
  let creatorId: string | null = null;

  await db.runTransaction(async (tx) => {
    const pairing = await tx.get(pairingRef);
    if (!pairing.exists) throw new HttpsError("not-found", "Invalid code");

    const data = pairing.data()!;
    if (data.expiresAt.toDate() < new Date()) {
      throw new HttpsError("deadline-exceeded", "Code expired");
    }

    creatorId = data.creatorId;
    if (creatorId === uid) {
      throw new HttpsError("invalid-argument", "Cannot pair with yourself");
    }

    const creatorRef = db.collection("users").doc(creatorId!);
    const acceptorRef = db.collection("users").doc(uid);

    tx.update(creatorRef, { partnerId: uid });
    tx.update(acceptorRef, { partnerId: creatorId });
    tx.delete(pairingRef);
  });

  // Send FCM to both
  if (creatorId) {
    const [creatorSnap, acceptorSnap] = await Promise.all([
      db.collection("users").doc(creatorId).get(),
      db.collection("users").doc(uid).get(),
    ]);
    const creatorToken = creatorSnap.data()?.fcmToken;
    const acceptorToken = acceptorSnap.data()?.fcmToken;
    const creatorName = creatorSnap.data()?.displayName ?? "Your partner";
    const acceptorName = acceptorSnap.data()?.displayName ?? "Your partner";

    const notifications = [];
    if (creatorToken) {
      notifications.push(
        admin.messaging().send({
          token: creatorToken,
          data: { title: "Cardea", body: `You're now connected with ${acceptorName}!` },
        })
      );
    }
    if (acceptorToken) {
      notifications.push(
        admin.messaging().send({
          token: acceptorToken,
          data: { title: "Cardea", body: `You're now connected with ${creatorName}!` },
        })
      );
    }
    await Promise.allSettled(notifications);
  }

  return { success: true };
});

// --- onRunCompleted ---
export const onRunCompleted = onDocumentCreated("runCompletions/{docId}", async (event) => {
  const data = event.data?.data();
  if (!data) return;

  const userId = data.userId;
  const userDoc = await db.collection("users").doc(userId).get();
  const partnerId = userDoc.data()?.partnerId;
  if (!partnerId) return;

  const partnerDoc = await db.collection("users").doc(partnerId).get();
  const partnerToken = partnerDoc.data()?.fcmToken;
  if (!partnerToken) return;

  const userName = userDoc.data()?.displayName ?? "Your partner";

  await admin.messaging().send({
    token: partnerToken,
    data: {
      title: "Cardea",
      body: `${userName} just finished their run!`,
      completionId: event.data?.id ?? "",
    },
  });
});

// --- cleanupOldCompletions (weekly) ---
export const cleanupOldCompletions = onSchedule("every sunday 02:00", async () => {
  const cutoff = Date.now() - 90 * 24 * 60 * 60 * 1000;
  const old = await db
    .collection("runCompletions")
    .where("timestamp", "<", cutoff)
    .limit(500)
    .get();

  const batch = db.batch();
  old.docs.forEach((doc) => batch.delete(doc.ref));
  await batch.commit();
});
