# Nimittam UI

Clean UI-only Android codebase for the Nimittam AI chat application.

## Structure

```
Android/src/app/src/main/
├── java/com/google/ai/edge/gallery/
│   ├── MainActivity.kt
│   ├── common/HapticFeedbackManager.kt
│   └── ui/
│       ├── components/          # 9 UI components
│       ├── navigation/          # Navigation graph
│       ├── screens/             # 8 screens
│       ├── theme/               # 5 theme files
│       └── viewmodels/          # 5 viewmodels (UI state only)
└── res/values/                  # strings, themes
```

## Files

- **30 Kotlin files** - UI-only code (screens, components, viewmodels, theme)
- **2 XML files** - Basic Android resources

## Note

This is a stripped UI-only version. All backend logic, LLM integration, database, and business logic have been removed.
