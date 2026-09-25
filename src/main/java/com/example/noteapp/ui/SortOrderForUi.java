package com.example.noteapp.ui;

import com.example.noteapp.repository.SortOrder;

/** Maps the sort combo box index to the repository {@link SortOrder}. */
enum SortOrderForUi {
    UPDATED_DESC, UPDATED_ASC, CREATED_DESC, CREATED_ASC, TITLE_ASC, TITLE_DESC;

    static SortOrderForUi fromIndex(int index) {
        SortOrderForUi[] values = values();
        return values[Math.max(0, Math.min(index, values.length - 1))];
    }

    SortOrder toSortOrder() { return SortOrder.valueOf(name()); }
}
