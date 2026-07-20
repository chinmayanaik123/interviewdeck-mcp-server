package com.interviewdeck.mcp.service;

import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.FieldValue;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.SetOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.UserRecord;
import com.google.firebase.cloud.FirestoreClient;
import com.interviewdeck.mcp.model.UserData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ExecutionException;

@Service
public class FirebaseUserService {

    private static final Logger log = LoggerFactory.getLogger(FirebaseUserService.class);

    public String resolveUid(String userIdOrEmail) {
        if (userIdOrEmail == null || userIdOrEmail.isBlank()) return null;
        if (!userIdOrEmail.contains("@")) return userIdOrEmail;
        try {
            UserRecord user = FirebaseAuth.getInstance().getUserByEmail(userIdOrEmail);
            return user.getUid();
        } catch (Exception e) {
            log.error("Could not resolve email {} to UID", userIdOrEmail, e);
            return null;
        }
    }

    public UserData getUserData(String uid) {
        try {
            Firestore db = FirestoreClient.getFirestore();
            DocumentSnapshot doc = db.collection("users").document(uid).get().get();

            if (!doc.exists()) {
                return new UserData(List.of(), List.of(), Map.of());
            }

            List<String> bookmarks = doc.contains("bookmarks")
                    ? (List<String>) doc.get("bookmarks")
                    : List.of();
            List<String> progress = doc.contains("progress")
                    ? (List<String>) doc.get("progress")
                    : List.of();
            Map<String, String> notes = doc.contains("notes")
                    ? (Map<String, String>) doc.get("notes")
                    : Map.of();

            return new UserData(
                    bookmarks != null ? bookmarks : List.of(),
                    progress != null ? progress : List.of(),
                    notes != null ? notes : Map.of()
            );
        } catch (InterruptedException | ExecutionException e) {
            log.error("Failed to fetch user data for uid={}", uid, e);
            return new UserData(List.of(), List.of(), Map.of());
        }
    }

    public boolean markQuestionComplete(String uid, String questionId) {
        try {
            Firestore db = FirestoreClient.getFirestore();
            db.collection("users").document(uid)
                    .set(Map.of("progress", FieldValue.arrayUnion(questionId)), SetOptions.merge())
                    .get();
            return true;
        } catch (Exception e) {
            log.error("Failed to mark question complete for uid={}, qid={}", uid, questionId, e);
            return false;
        }
    }

    public boolean markQuestionsComplete(String uid, List<String> questionIds) {
        try {
            Firestore db = FirestoreClient.getFirestore();
            db.collection("users").document(uid)
                    .set(Map.of("progress", FieldValue.arrayUnion(questionIds.toArray())), SetOptions.merge())
                    .get();
            return true;
        } catch (Exception e) {
            log.error("Failed to mark questions complete for uid={}", uid, e);
            return false;
        }
    }

    public boolean addNote(String uid, String questionId, String note) {
        try {
            Firestore db = FirestoreClient.getFirestore();
            db.collection("users").document(uid)
                    .set(Map.of("notes", Map.of(questionId, note)), SetOptions.merge())
                    .get();
            return true;
        } catch (Exception e) {
            log.error("Failed to add note for uid={}, qid={}", uid, questionId, e);
            return false;
        }
    }
}
