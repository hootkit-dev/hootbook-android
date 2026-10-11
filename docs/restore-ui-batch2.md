# Restore UI — Batch 2

Restores `origin/main` (`c37b280`) Transactions and Add layouts on Core. Synced IDs are strings. State contains generated Core transactions, drafts, categories, accounts, subscriptions, people and debts; joins use ID maps. The old `TransactionLookups` facade is not restored.

## Destinations and retained behavior

- `Transactions`: original list, search, supported filters and sorts, summary sheet, selection, bulk edits, delete/undo, recycle-bin purge, export, and the original two-pane layout at 720 dp.
- `TransactionDetail`: receipt, image sharing, edit, duplicate, delete/purge, quick-template creation, recurrence, attachments, merchant icon picker, person navigation, and loan-kind linking/unlinking.
- `AddTransaction`: original transaction and subscription tabs, number pad, account/category pickers, templates, Core template suggestions and merchant completion. Template shortcuts, new expense/transfer, subscription payment and subscription creation retain their navigation routes. Share ingress continues to the existing AI flow.
- Paging requests 50 rows at a time when scrolling. Changes requery the visible range. Core supplies list order and summary values; adjacent date sections retain that order for all three sorts. Query errors remain observable so later ChangeBus changes can recover.
- All command paths guard submission. Add drafts retain partial amount input in SavedStateHandle. A successful ledger add is remembered before loan-link/template follow-ups, so retries cannot add the transaction twice.
- Core handles every conversion, total and schedule. Existing subscription edits preserve Core's next-due value; new subscriptions from transactions use `subscriptionFromTransaction`, and payments use `ledger.add(subscriptionId=...)`.
- CSV was relocated to `presentation/export/CoreTransactionCsv.kt`; it exports all pages, reports progress, and rejects changing totals, duplicate IDs and revision changes. Export dates come from oldest/newest Core queries.
- Core localizes builtin category/subcategory `displayName` using its locale (`services/categories.rs` name resolution). The unused Kotlin `CategoryNames.kt` layer is deleted.

## Removed UI and behavior

Paths below are relative to `app/src/main/java/io/github/hootkit_dev/hootbook/presentation/`.

| File(s) | Removed element or behavior | Core limitation |
| --- | --- | --- |
| `ui/features/transactions/TransactionsFilterBottomSheet.kt`, `TransactionsState.kt` | Amount-range and subcategory filters; smallest-amount and merchant-name sorts | `TransactionFilter` and `SortOrder` support only the retained options. |
| `common/Filters.kt`, `ui/components/TypeFilterIcon.kt`, `ui/features/add/TransactionTabContent.kt`, `ui/features/transactions/TransactionDetailScreen.kt` | Credit transaction kind | Core has credit-card accounts, but no separate credit transaction kind. |
| `ui/features/transactions/TransactionDetailScreen.kt` | Marking ordinary expense/income as loans | `linkTransaction` requires `LoanOut`/`LoanIn`. No kind conversion or category removal is used to emulate marking. |
| `ui/features/transactions/LoanPersonSheet.kt` (replaces the old general debt-entry sheet at this call site) | Independent linked-loan amount, date, due date, title, account, category, attachments and opposite-direction overrides | Linking accepts only transaction ID, person ID and an allowed debt kind; it derives the linked record from the transaction. Editing debt to emulate overrides would replace/remove the underlying ledger entry. |
| `ui/features/add/TransactionTabContent.kt` | Loan due-date input for a transaction being added and linked | The ledger-add/link path has no due-date field. A debt update would rewrite the new transaction rather than just set link metadata. |
| `ui/features/add/TransactionTabContent.kt`, `ui/features/transactions/TransactionDetailScreen.kt` | Category/subcategory selection for transfers, investments and loan kinds | Core permits transaction categories only on expense/income. |
| `ui/features/transactions/TransactionDetailScreen.kt` | Recurrence for non-expenses; saving transfer/loan quick templates | Core subscription-from-transaction requires expense; quick templates allow expense, income and investment. |
| `ui/features/transactions/TransactionDetailViewModel.kt` | Recalculating an existing subscription's next due date from a historical transaction edit | Core has no equivalent rescheduling command for that edit. Stored next due is preserved; Core advances it on payments. |
| `ui/features/transactions/TransactionDetailScreen.kt` | Same-merchant matching count, preview/exclusions and automatic category propagation to matching history | Core has no exact merchant-match query. Text search is not an equivalent match set. Explicit selection and Core bulk updates remain. |
| `ui/features/add/TransactionTabContent.kt`, `EntrySuggestionViews.kt`, `ui/components/CategorySelectionSheet.kt` | Locally ranked common/recent categories; merchant-completion category/account/subcategory autofill and metadata | Core supplies template suggestions and merchant names, not these history rankings or merchant-prefill records. |
| `ui/features/add/TransactionTabContent.kt` | Projected overdraft warning/override | Core exposes balances and validates commands, not the old projected-after-entry balance. Kotlin does not calculate one. |
| `ui/features/transactions/TransactionDetailScreen.kt`, `ui/components/TransactionItem.kt` | Balance-after-transaction receipt/row values | Core Transaction has no running balance-after value. |
| `ui/features/add/SubscriptionTabContent.kt` | Subscription attachments | Core Subscription/SubscriptionDraft have no attachments field. Transaction attachments remain. |
| `ui/components/AttachmentSection.kt` | Legacy Drive/remote-URL attachment handling | Core stores relative attachment references; previews use CoreAttachmentStore. |
| `ui/components/CategoryChip.kt` | Category description subtitle | Core Category has no description field. |

The old Kotlin currency-total aggregation helper is not restored. Income, expense and net come directly from `LedgerSummary`; missing conversion completeness is shown, never replaced with fabricated totals.

## Remaining temporary mounts

27 mounts remain in `HootbookNavHost.kt`: OnBoarding, Home, Analytics, ManageAccounts, AccountCategoryRoute, AccountDetail, AddAccount, Categories, Budgets, BudgetDetail, BudgetHistory, Subscriptions, QuickTemplates, LendBorrow, Contacts, PersonDetail, Investments, AiAssistant, Settings, CurrencySettings, DataPrivacy, CloudSync, Profile, MerchantIcons, Appearance, AppLockSettings, NotificationSettings. The standalone MerchantIcons management destination is separate from the restored transaction MerchantIconSheet.

## Validation

- Fake-Core tests cover each Core ViewModel's applicable initial state, ChangeBus refresh and recovery, latest query wins, command success/error and submission guards. Additional checks cover pagination, exact Core totals/conversion results, draft restoration, partial-command retry, loan-kind constraints, unmarking, subscription commands, full export and sort-preserving sections.
- Merchant icon VM tests use a fake HTTP transport and the real platform asset store for search cancellation, failures, guarded downloads and reset.
- Existing ingress/navigation tests continue to exercise new expense, transfer, subscription, template/payment and share routing.
- Mandatory `:app:compileStandardDebugKotlin :app:testStandardDebugUnitTest` and `:app:compileStandardDebugAndroidTestKotlin` commands pass: **470 tests, zero failures/errors/skips**. Debug APK assembly also passes.
- API 35 emulator smoke test passed: new-expense shortcut, original number pad and category picker, save, list and summary, receipt, ordinary-expense action menu without loan marking, subscription shortcut, and a transfer shortcut while the subscription form was already open. The latter verifies route-specific form identity. Receipt clock formatting and compact amount formatting have regression tests.
- Captured evidence: [Transactions](validation/restore-ui-batch2/transactions.png), [receipt](validation/restore-ui-batch2/detail.png), [subscription entry](validation/restore-ui-batch2/subscription.png), [transfer entry](validation/restore-ui-batch2/transfer.png).
- A same-dataset `origin/main` screenshot comparison has not been performed. No claim of pixel-level equivalence or release/R8 validation is made.

No Core lock, Core source/worktree, core-platform public API, or remote configuration was changed. No open product decisions remain.
