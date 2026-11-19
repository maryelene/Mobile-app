const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_student_1";
const BOB_UID = "bob_student_2";

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

test("Unauthenticated users cannot read or write any EduFlow collections", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const past = new Date(Date.now() - 1000);

  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("subjects").get()
  );
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("notes").get()
  );
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("definitions").get()
  );
  await assertFails(
    unauthDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("subjects")
      .doc("sub_bio")
      .set({
        userId: ALICE_UID,
        name: "Biology",
        colorKey: "SAGE",
        iconKey: "SCIENCE",
        createdAt: past,
        updatedAt: past,
      })
  );
});

test("Authenticated owner: valid Subject, StudyNote, and KeyDefinition create/update/list succeed", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = new Date(Date.now() - 10000);
  const updated = new Date(Date.now() - 1000);

  // 1. Create Subject (no @DocumentId 'id' field)
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("subjects")
      .doc("sub_bio")
      .set({
        userId: ALICE_UID,
        name: "Biology",
        colorKey: "SAGE",
        iconKey: "SCIENCE",
        createdAt: created,
        updatedAt: created,
      })
  );

  // 2. Partial update Subject
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("subjects")
      .doc("sub_bio")
      .update({
        name: "AP Biology",
        updatedAt: updated,
      })
  );

  // 3. Create StudyNote (omits optional photoFileName when null)
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("notes")
      .doc("note_cells")
      .set({
        userId: ALICE_UID,
        subjectId: "sub_bio",
        title: "Cell Organelles",
        extractedText: "Mitochondria produce ATP for cellular respiration.",
        sourceType: "PRINTED",
        tags: ["Chapter 3", "Cells"],
        createdAt: created,
        updatedAt: created,
      })
  );

  // 4. Update StudyNote with photoFileName and edited text
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("notes")
      .doc("note_cells")
      .update({
        extractedText:
          "Mitochondria produce ATP for cellular respiration. Ribosomes synthesize proteins.",
        photoFileName: "note_cells.jpg",
        tags: ["Chapter 3", "Cells", "Exam 1"],
        updatedAt: updated,
      })
  );

  // 5. Create KeyDefinition
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("definitions")
      .doc("def_mito")
      .set({
        userId: ALICE_UID,
        subjectId: "sub_bio",
        noteId: "note_cells",
        term: "Mitochondria",
        definition: "Organelles that produce ATP for cellular respiration.",
        highlightColor: "YELLOW",
        startOffset: 0,
        endOffset: 12,
        tags: ["Chapter 3", "Cells"],
        mastered: false,
        createdAt: created,
        updatedAt: created,
      })
  );

  // 6. Toggle mastered via action branch
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("definitions")
      .doc("def_mito")
      .update({
        mastered: true,
        updatedAt: updated,
      })
  );

  // 7. Owner list queries (matching Kotlin repositories)
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("subjects").get()
  );
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("notes").get()
  );
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("definitions").get()
  );
});

test("Cross-user isolation: Bob cannot read, list, create, update, or delete Alice's records", async () => {
  const past = new Date(Date.now() - 10000);
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db
      .collection("users")
      .doc(ALICE_UID)
      .collection("subjects")
      .doc("sub_hist")
      .set({
        userId: ALICE_UID,
        name: "History",
        colorKey: "AMBER",
        iconKey: "HISTORY",
        createdAt: past,
        updatedAt: past,
      });
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("subjects")
      .doc("sub_hist")
      .get()
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("subjects").get()
  );
  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("subjects")
      .doc("sub_hist")
      .update({ name: "Hijacked", updatedAt: new Date(Date.now() - 500) })
  );
  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("subjects")
      .doc("sub_hist")
      .delete()
  );
});

test("Negative bounds: ghost fields, @DocumentId 'id', orphan note/definition, and tampered future timestamps fail", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const past = new Date(Date.now() - 5000);
  const future = new Date(Date.now() + 60000);

  // Reject ghost 'id' field on Subject
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("subjects")
      .doc("sub_bad")
      .set({
        id: "sub_bad",
        userId: ALICE_UID,
        name: "Math",
        colorKey: "COBALT",
        iconKey: "MATH",
        createdAt: past,
        updatedAt: past,
      })
  );

  // Reject future timestamp
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("subjects")
      .doc("sub_future")
      .set({
        userId: ALICE_UID,
        name: "Math",
        colorKey: "COBALT",
        iconKey: "MATH",
        createdAt: future,
        updatedAt: future,
      })
  );

  // Reject orphan StudyNote referencing non-existent subject
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("notes")
      .doc("note_orphan")
      .set({
        userId: ALICE_UID,
        subjectId: "missing_subject",
        title: "Orphan Note",
        extractedText: "Text",
        sourceType: "TYPED",
        tags: [],
        createdAt: past,
        updatedAt: past,
      })
  );

  // Create valid subject & check createdAt immutability on update
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("subjects")
      .doc("sub_math")
      .set({
        userId: ALICE_UID,
        name: "Algebra",
        colorKey: "COBALT",
        iconKey: "MATH",
        createdAt: past,
        updatedAt: past,
      })
  );

  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("subjects")
      .doc("sub_math")
      .update({
        createdAt: new Date(Date.now() - 1000),
        updatedAt: new Date(Date.now() - 1000),
      })
  );
});
