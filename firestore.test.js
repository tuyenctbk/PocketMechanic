const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

function validEquipmentPayload(uid, overrides = {}) {
  const now = new Date(Date.now() - 2000);
  return {
    userId: uid,
    name: "2018 Honda Civic EX",
    category: "VEHICLE",
    makeModel: "Honda Civic 1.5T",
    yearOrSerial: "2018",
    notes: "Squeal on cold start",
    createdAt: now,
    updatedAt: now,
    ...overrides,
  };
}

function validDiagnosticPayload(uid, overrides = {}) {
  const now = new Date(Date.now() - 2000);
  return {
    userId: uid,
    title: "Serpentine Belt Squeal",
    category: "VEHICLE",
    mode: "AUDIO",
    equipmentName: "2018 Honda Civic EX",
    severity: "MEDIUM",
    primaryCulprit: "Glazed serpentine belt or weak tensioner pulley",
    confidencePercent: 86,
    summary: "High-pitched chirping synchronized with engine RPM indicates belt slip or idler bearing wear.",
    safetyWarnings: ["Turn off ignition and remove key before touching pulleys."],
    diySteps: ["Inspect belt ribs for glazing or cracks.", "Check tensioner deflection."],
    partsTools: ["14mm socket wrench", "Replacement K060812 serpentine belt"],
    sourceEntries: ["Honda Service Manual||https://example.com/manual"],
    resolved: false,
    createdAt: now,
    updatedAt: now,
    ...overrides,
  };
}

test("Unauthenticated users cannot read or write equipment or diagnostics", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection(`users/${ALICE_UID}/equipment`).get());
  await assertFails(unauthDb.collection(`users/${ALICE_UID}/diagnostics`).get());
  await assertFails(
    unauthDb.collection(`users/${ALICE_UID}/equipment`).doc("eq_1").set(validEquipmentPayload(ALICE_UID))
  );
  await assertFails(
    unauthDb.collection(`users/${ALICE_UID}/diagnostics`).doc("diag_1").set(validDiagnosticPayload(ALICE_UID))
  );
});

test("Owner can create, list, update, and delete equipment", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const col = aliceDb.collection("users").doc(ALICE_UID).collection("equipment");

  await assertSucceeds(col.doc("eq_1").set(validEquipmentPayload(ALICE_UID)));
  await assertSucceeds(col.get());

  await assertSucceeds(
    col.doc("eq_1").update({
      notes: "Replaced serpentine belt at 72k miles",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Reject immutable createdAt modification
  await assertFails(
    col.doc("eq_1").update({
      createdAt: new Date(Date.now() - 100),
      updatedAt: new Date(Date.now() - 100),
    })
  );

  // Reject ghost field
  await assertFails(
    col.doc("eq_2").set(validEquipmentPayload(ALICE_UID, { id: "eq_2" }))
  );

  await assertSucceeds(col.doc("eq_1").delete());
});

test("Owner can create, query, toggle resolved, and delete diagnostics", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const col = aliceDb.collection("users").doc(ALICE_UID).collection("diagnostics");

  await assertSucceeds(col.doc("diag_1").set(validDiagnosticPayload(ALICE_UID)));
  await assertSucceeds(col.orderBy("createdAt", "desc").get());

  // Toggle resolved status
  await assertSucceeds(
    col.doc("diag_1").update({
      resolved: true,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Reject future timestamp
  await assertFails(
    col.doc("diag_2").set(validDiagnosticPayload(ALICE_UID, { createdAt: new Date(Date.now() + 60000) }))
  );

  // Reject invalid confidence bounds
  await assertFails(
    col.doc("diag_3").set(validDiagnosticPayload(ALICE_UID, { confidencePercent: 140 }))
  );

  // Reject invalid mode enum
  await assertFails(
    col.doc("diag_4").set(validDiagnosticPayload(ALICE_UID, { mode: "MAGIC" }))
  );

  await assertSucceeds(col.doc("diag_1").delete());
});

test("Cross-user access to equipment and diagnostics is denied", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const adminDb = context.firestore();
    await adminDb.collection("users").doc(BOB_UID).collection("equipment").doc("bob_eq").set(validEquipmentPayload(BOB_UID));
    await adminDb.collection("users").doc(BOB_UID).collection("diagnostics").doc("bob_diag").set(validDiagnosticPayload(BOB_UID));
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(aliceDb.collection("users").doc(BOB_UID).collection("equipment").get());
  await assertFails(aliceDb.collection("users").doc(BOB_UID).collection("equipment").doc("bob_eq").get());
  await assertFails(aliceDb.collection("users").doc(BOB_UID).collection("diagnostics").get());
  await assertFails(aliceDb.collection("users").doc(BOB_UID).collection("diagnostics").doc("bob_diag").update({ resolved: true }));
  await assertFails(aliceDb.collection("users").doc(BOB_UID).collection("diagnostics").doc("bob_diag").delete());

  // Cannot spoof another userId inside own subcollection
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("diagnostics").doc("spoof_1").set(validDiagnosticPayload(BOB_UID))
  );
});
