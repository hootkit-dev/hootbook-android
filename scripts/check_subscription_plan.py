#!/usr/bin/env python3
"""Source-contract regression checks; not Android runtime or database tests."""

import argparse
from pathlib import Path
import re
import sys


def function_body(source, name):
    """Read a Kotlin function body using balanced braces (for these source files)."""
    match = re.search(r"\bfun\s+" + re.escape(name) + r"\s*\(", source)
    if match is None:
        raise AssertionError(f"Missing function: {name}")
    start = source.index("{", match.end())
    depth = 1
    for end in range(start + 1, len(source)):
        depth += (source[end] == "{") - (source[end] == "}")
        if depth == 0:
            return source[start + 1:end]
    raise AssertionError(f"Unclosed function: {name}")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[1])
    args = parser.parse_args()
    base = args.root / "app/src/main/java/io/github/hootkit_dev/hootbook"

    def read(path):
        return (base / path).read_text()

    add = read("presentation/ui/features/add/AddViewModel.kt")
    save = function_body(add, "saveSubscription")
    # Narrow to the create branch rather than accepting the edit branch's date.
    create = save[save.index("addSubscriptionUseCase.execute("):]
    subscriptions = read("presentation/ui/features/subscriptions/SubscriptionsViewModel.kt")
    repository = read("data/repository/SubscriptionRepository.kt")
    use_cases = read("domain/usecase/AddSubscriptionUseCase.kt") + read("domain/usecase/UpdateSubscriptionUseCase.kt")
    ordinary = read("domain/usecase/AddTransactionUseCase.kt")
    checks = {
        "saving a subscription does not create a transaction":
            "addTransactionUseCase.execute(" not in save,
        "new plan preserves selected next payment date":
            "nextPaymentDate = state.nextPaymentDate," in create,
        "new plan has no completed payment":
            "lastPaidDate = null" in create,
        "plan storage has no transaction or balance dependency":
            not re.search(r"\b(?:AccountBalanceRepository|TransactionRepository)\b", repository + use_cases),
        "delete and undo only change subscription visibility":
            "subscriptionRepository.hideSubscription(" in function_body(subscriptions, "hideSubscription")
            and "subscriptionRepository.unhideSubscription(" in function_body(subscriptions, "undoHide")
            and not re.search(r"(?:accountBalanceRepository|transactionRepository)\.",
                              function_body(subscriptions, "hideSubscription") + function_body(subscriptions, "undoHide")),
        "mark paid updates schedule without recording an expense":
            "subscriptionRepository.updatePaymentStatus(" in function_body(subscriptions, "markAsPaid")
            and not re.search(r"(?:accountBalanceRepository|transactionRepository|addTransactionUseCase)\.",
                              function_body(subscriptions, "markAsPaid")),
        "ordinary transaction creation still updates balances":
            "accountBalanceRepository.insertTransactionBalance(" in ordinary,
    }
    for name, passed in checks.items():
        print(f"{'PASS' if passed else 'FAIL'}: {name}")
    return 0 if all(checks.values()) else 1


if __name__ == "__main__":
    sys.exit(main())
