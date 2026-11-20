// @ts-check
// Uses VoiceJournalApiClient, MAX_PAGE_SIZE, clampPageSize and OpenAPI DTO typedefs from api-client.js.

/**
 * @template {HTMLElement} T
 * @param {string} id
 * @returns {T}
 */
function requireElementById(id) {
    const el = document.getElementById(id);
    if (!el) {
        throw new Error(`Required element #${id} not found in DOM`);
    }
    return /** @type {T} */ (el);
}

/**
 * Returns the browser's IANA timezone ID (e.g. "Europe/Berlin") or null if unavailable.
 * @returns {string | null}
 */
function getBrowserZoneId() {
    try {
        return Intl.DateTimeFormat().resolvedOptions().timeZone || null;
    } catch {
        return null;
    }
}

/**
 * Validates an optional IANA timezone ID; returns undefined if invalid so Intl falls back to local.
 * @param {string | null | undefined} zoneId
 * @returns {string | undefined}
 */
function resolveValidZoneId(zoneId) {
    if (!zoneId) return undefined;
    try {
        Intl.DateTimeFormat('de-DE', { timeZone: zoneId });
        return zoneId;
    } catch {
        return undefined;
    }
}

/**
 * Formats an epoch millisecond timestamp as a German day header in the entry's timezone.
 * @param {number} epochMs
 * @param {string | null | undefined} zoneId
 * @returns {string}
 */
function formatDateHeader(epochMs, zoneId) {
    const timeZone = resolveValidZoneId(zoneId);
    return new Date(epochMs).toLocaleDateString('de-DE', {
        weekday: 'long',
        year: 'numeric',
        month: 'long',
        day: 'numeric',
        timeZone
    });
}

/**
 * Formats start and optional stop epoch milliseconds as `HH:mm` or `HH:mm - HH:mm` in the entry's timezone.
 * @param {number} startMs
 * @param {number | null | undefined} stopMs
 * @param {string | null | undefined} zoneId
 * @returns {string}
 */
function formatTimeRange(startMs, stopMs, zoneId) {
    const timeZone = resolveValidZoneId(zoneId);
    /** @type {Intl.DateTimeFormatOptions} */
    const timeOptions = { hour: '2-digit', minute: '2-digit', timeZone };
    let timeString = new Date(startMs).toLocaleTimeString('de-DE', timeOptions);
    if (stopMs != null) {
        const endTimeString = new Date(stopMs).toLocaleTimeString('de-DE', timeOptions);
        timeString += ` - ${endTimeString}`;
    }
    return timeString;
}

/**
 * Formats epoch milliseconds into `YYYY-MM-DDTHH:mm` for `<input type="datetime-local">`
 * in the entry's IANA timezone (or browser local timezone if null/invalid).
 * @param {number} epochMs
 * @param {string | null | undefined} zoneId
 * @returns {string}
 */
function formatDateTimeLocal(epochMs, zoneId) {
    const timeZone = resolveValidZoneId(zoneId);
    const parts = new Intl.DateTimeFormat('en-CA', {
        year: 'numeric',
        month: '2-digit',
        day: '2-digit',
        hour: '2-digit',
        minute: '2-digit',
        hourCycle: 'h23',
        timeZone
    }).formatToParts(new Date(epochMs));

    /** @type {Record<string, string>} */
    const map = {};
    for (const part of parts) {
        if (part.type !== 'literal') {
            map[part.type] = part.value;
        }
    }
    return `${map.year}-${map.month}-${map.day}T${map.hour}:${map.minute}`;
}

/**
 * Computes timezone offset in milliseconds for a given UTC instant and IANA zone.
 * @param {number} utcMs
 * @param {string} timeZone
 * @returns {number}
 */
function getTimeZoneOffsetMs(utcMs, timeZone) {
    const parts = new Intl.DateTimeFormat('en-US', {
        year: 'numeric',
        month: '2-digit',
        day: '2-digit',
        hour: '2-digit',
        minute: '2-digit',
        second: '2-digit',
        hourCycle: 'h23',
        timeZone
    }).formatToParts(new Date(utcMs));

    /** @type {Record<string, number>} */
    const map = {};
    for (const part of parts) {
        if (part.type !== 'literal') {
            map[part.type] = parseInt(part.value, 10);
        }
    }
    const asUtc = Date.UTC(
        map.year,
        map.month - 1,
        map.day,
        map.hour,
        map.minute,
        map.second
    );
    return asUtc - utcMs;
}

/**
 * Parses a `<input type="datetime-local">` string (`YYYY-MM-DDTHH:mm`) as wall-clock time
 * in the specified IANA timezone (falling back to browser local time if zoneId is absent/invalid).
 * @param {string} localStr
 * @param {string | null | undefined} zoneId
 * @returns {number}
 */
function parseDateTimeLocalInZone(localStr, zoneId) {
    const timeZone = resolveValidZoneId(zoneId);
    if (!timeZone) {
        return new Date(localStr).getTime();
    }
    const match = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})$/.exec(localStr);
    if (!match) {
        return new Date(localStr).getTime();
    }
    const year = parseInt(match[1], 10);
    const month = parseInt(match[2], 10);
    const day = parseInt(match[3], 10);
    const hour = parseInt(match[4], 10);
    const minute = parseInt(match[5], 10);

    const utcGuess = Date.UTC(year, month - 1, day, hour, minute);
    const offset1 = getTimeZoneOffsetMs(utcGuess, timeZone);
    const candidate = utcGuess - offset1;
    const offset2 = getTimeZoneOffsetMs(candidate, timeZone);
    return utcGuess - offset2;
}

function initVoiceJournalApp() {
    /** @type {HTMLSelectElement} */
    const categorySelect = requireElementById('category-select');
    /** @type {HTMLDivElement} */
    const journalEntriesContainer = requireElementById('journal-entries');

    // Create Sentinel dynamically
    const scrollSentinel = document.createElement('div');
    scrollSentinel.id = 'scroll-sentinel';
    scrollSentinel.style.height = '20px';
    scrollSentinel.style.textAlign = 'center';
    scrollSentinel.style.color = '#888';
    scrollSentinel.style.width = '100%';

    /** @type {HTMLInputElement} */
    const apiUrlInput = requireElementById('api-url');
    /** @type {HTMLButtonElement} */
    const refreshApiButton = requireElementById('refresh-api');
    /** @type {HTMLButtonElement} */
    const settingsToggle = requireElementById('settings-toggle');
    /** @type {HTMLDivElement} */
    const settingsPanel = requireElementById('settings-panel');
    /** @type {HTMLInputElement} */
    const pageSizeInput = requireElementById('page-size');

    // API Host aus LocalStorage laden oder Standardwert setzen
    let apiHost = localStorage.getItem('voicejournal_api_host') || 'localhost';
    const apiClient = new VoiceJournalApiClient(`http://${apiHost}:8080`);
    apiUrlInput.value = apiHost;

    // Paginierung Status
    let currentPage = 1;
    const storedPageSize = parseInt(localStorage.getItem('voicejournal_page_size') || '', 10);
    let pageSize = Number.isNaN(storedPageSize) ? 5 : clampPageSize(storedPageSize);
    pageSizeInput.value = String(pageSize);

    /** @type {number | null} */
    let currentCategoryId = null;
    /** @type {string | null} */
    let lastRenderedDate = null;
    let isLoading = false;
    let hasMore = true;

    // Intersection Observer für Infinite Scroll
    const observer = new IntersectionObserver((entries) => {
        if (entries[0].isIntersecting && !isLoading && hasMore && currentCategoryId !== null) {
            fetchJournalEntries(currentCategoryId, true);
        }
    }, {
        root: journalEntriesContainer,
        threshold: 0.1
    });

    observer.observe(scrollSentinel);

    // Modal Elemente
    /** @type {HTMLDivElement} */
    const modal = requireElementById('edit-modal');
    const closeButton = /** @type {HTMLElement | null} */ (document.querySelector('.close-button'));
    /** @type {HTMLButtonElement} */
    const saveButton = requireElementById('save-button');
    /** @type {HTMLButtonElement} */
    const cancelButton = requireElementById('cancel-button');
    /** @type {HTMLButtonElement} */
    const deleteButton = requireElementById('delete-button');
    /** @type {HTMLTextAreaElement} */
    const editContentTextarea = requireElementById('edit-content');
    /** @type {HTMLInputElement} */
    const editDateInput = requireElementById('edit-date');
    /** @type {HTMLInputElement} */
    const editEndDateInput = requireElementById('edit-end-date');
    /** @type {HTMLDivElement} */
    const editCategoriesContainer = requireElementById('edit-categories');
    /** @type {HTMLButtonElement} */
    const newEntryButton = requireElementById('new-entry-button');
    /** @type {HTMLHeadingElement} */
    const modalTitle = requireElementById('modal-title');

    /** @type {JournalEntryDto | null} */
    let currentEditingEntry = null;
    /** @type {CategoryDto[]} */
    let allCategories = [];

    // Kategorien laden und Dropdown füllen
    async function fetchCategories() {
        try {
            const categories = await apiClient.getCategories();
            allCategories = categories; // Speichern für späteren Zugriff
            populateCategoryDropdown(categories);
        } catch (error) {
            const message = error instanceof Error ? error.message : String(error);
            console.error('Fehler beim Laden der Kategorien:', error);
            journalEntriesContainer.innerHTML = `<p style="color: red; padding: 20px;">Fehler beim Laden der Kategorien.<br>
            API URL: ${apiClient.getBaseUrl()}/categories<br>
            Fehler: ${message}<br>
            Ist das Backend gestartet?</p>`;
        }
    }

    /**
     * @param {CategoryDto[]} categories
     */
    function populateCategoryDropdown(categories) {
        [...categories]
            .sort((a, b) => a.orderIndex - b.orderIndex)
            .forEach(category => {
                const option = document.createElement('option');
                option.value = String(category.id);
                option.textContent = category.category;
                categorySelect.appendChild(option);
            });
    }

    /**
     * Journal-Einträge für eine Kategorie laden
     * @param {number | null} categoryId
     * @param {boolean} [append=false]
     */
    async function fetchJournalEntries(categoryId, append = false) {
        if (categoryId === null || Number.isNaN(categoryId)) {
            journalEntriesContainer.innerHTML = '';
            scrollSentinel.textContent = '';
            currentCategoryId = null;
            return;
        }

        if (isLoading) return;
        isLoading = true;

        if (!append) {
            currentPage = 1;
            currentCategoryId = categoryId;
            lastRenderedDate = null;
            hasMore = true;
            journalEntriesContainer.innerHTML = '';
            journalEntriesContainer.appendChild(scrollSentinel);
            scrollSentinel.textContent = 'Lade Einträge...';
        } else {
            scrollSentinel.textContent = 'Lade mehr...';
        }

        try {
            const selectedCategory = allCategories.find(c => c.id === categoryId) || null;
            const requestedSize = selectedCategory && selectedCategory.showAll
                ? MAX_PAGE_SIZE
                : pageSize;
            const effectivePageSize = clampPageSize(requestedSize);
            const showDateHeaders = selectedCategory ? selectedCategory.showDate : true;

            const entries = await apiClient.getJournalEntriesByCategory(
                categoryId,
                currentPage,
                effectivePageSize
            );

            displayJournalEntries(entries, { append, showDateHeaders });

            // Check if we reached the end using the same clamped limit as the server (1..500)
            if (entries.length < effectivePageSize) {
                hasMore = false;
                scrollSentinel.textContent = '';
            } else {
                scrollSentinel.textContent = ''; // Clear text, sentinel remains for observer
            }

            // Ensure sentinel is at the bottom
            journalEntriesContainer.appendChild(scrollSentinel);

            currentPage++;
        } catch (error) {
            console.error(`Fehler beim Laden der Journal-Einträge für Kategorie ${categoryId}:`, error);
            if (!append) {
                journalEntriesContainer.innerHTML = '<p>Fehler beim Laden der Einträge für die ausgewählte Kategorie.</p>';
            }
            scrollSentinel.textContent = 'Fehler beim Laden';
            journalEntriesContainer.appendChild(scrollSentinel);
        } finally {
            isLoading = false;
        }
    }

    /**
     * Journal-Einträge als Chat-Liste anzeigen
     * @param {JournalEntryDto[]} entries
     * @param {{ append: boolean, showDateHeaders: boolean }} options
     */
    function displayJournalEntries(entries, { append, showDateHeaders }) {
        if (entries.length === 0 && !append) {
            journalEntriesContainer.innerHTML = '<p>Keine Einträge für diese Kategorie gefunden.</p>';
            return;
        }

        const sortedEntries = [...entries].sort((a, b) => b.start_datetime - a.start_datetime);

        sortedEntries.forEach(entry => {
            if (showDateHeaders) {
                const dateString = formatDateHeader(entry.start_datetime, entry.zoneId);
                if (dateString !== lastRenderedDate) {
                    const dateHeader = document.createElement('div');
                    dateHeader.classList.add('date-header');
                    dateHeader.textContent = dateString;
                    journalEntriesContainer.appendChild(dateHeader);
                    lastRenderedDate = dateString;
                }
            }

            const bubble = document.createElement('div');
            bubble.classList.add('journal-entry');
            bubble.dataset.id = entry.id;

            // Long Click Logic für das Öffnen des Modals
            /** @type {ReturnType<typeof setTimeout> | null} */
            let pressTimer = null;
            let startX = 0;
            let startY = 0;

            /** @param {MouseEvent | TouchEvent} e */
            const startPress = (e) => {
                // Ignore checkbox clicks
                if (e.target instanceof HTMLInputElement && e.target.type === 'checkbox') return;

                if (e instanceof MouseEvent && e.button !== 0) return;

                // Koordinaten speichern
                if ('touches' in e && e.touches.length > 0) {
                    startX = e.touches[0].clientX;
                    startY = e.touches[0].clientY;
                } else if (e instanceof MouseEvent) {
                    startX = e.clientX;
                    startY = e.clientY;
                }

                if (pressTimer === null) {
                    pressTimer = setTimeout(() => {
                        openEditModal(entry);
                        pressTimer = null;
                    }, 600); // 600ms für Long Click
                }
            };

            const cancelPress = () => {
                if (pressTimer !== null) {
                    clearTimeout(pressTimer);
                    pressTimer = null;
                }
            };

            /**
             * Überprüfen, ob die Maus/Finger bewegt wurde um Long Press abzubrechen (z.B. beim Text markieren)
             * @param {MouseEvent | TouchEvent} e
             */
            const checkMove = (e) => {
                if (pressTimer === null) return;

                let currentX = startX;
                let currentY = startY;
                if ('touches' in e && e.touches.length > 0) {
                    currentX = e.touches[0].clientX;
                    currentY = e.touches[0].clientY;
                } else if (e instanceof MouseEvent) {
                    currentX = e.clientX;
                    currentY = e.clientY;
                }

                // Wenn Bewegung > 10 Pixel, dann abbrechen
                if (Math.abs(currentX - startX) > 10 || Math.abs(currentY - startY) > 10) {
                    cancelPress();
                }
            };

            bubble.addEventListener('mousedown', startPress);
            bubble.addEventListener('touchstart', startPress, { passive: true });

            bubble.addEventListener('mouseup', cancelPress);
            bubble.addEventListener('mouseleave', cancelPress);
            bubble.addEventListener('touchend', cancelPress);

            // Bei Bewegung prüfen (statt direkt abzubrechen bei touchmove)
            bubble.addEventListener('touchmove', checkMove, { passive: true });
            bubble.addEventListener('mousemove', checkMove);

            // Prevent context menu on long press
            bubble.addEventListener('contextmenu', (e) => {
                e.preventDefault();
                return false;
            });

            // Checkbox Logic
            let isCheckable = false;
            if (entry.categoryIds && entry.categoryIds.length > 0) {
                entry.categoryIds.forEach(catId => {
                    const cat = allCategories.find(c => c.id === catId);
                    if (cat && cat.checkable) {
                        isCheckable = true;
                    }
                });
            }

            const content = document.createElement('div');
            content.classList.add('entry-content');
            content.textContent = entry.content;

            if (isCheckable) {
                // Wrapper für Checkbox und Content
                const contentWrapper = document.createElement('div');
                contentWrapper.style.display = 'flex';
                contentWrapper.style.alignItems = 'flex-start';
                contentWrapper.style.gap = '10px';

                const checkbox = document.createElement('input');
                checkbox.type = 'checkbox';
                checkbox.checked = entry.checked || false;
                checkbox.style.marginTop = '4px'; // Better alignment with text
                checkbox.style.cursor = 'pointer';

                checkbox.addEventListener('change', async (e) => {
                    e.stopPropagation(); // Stop bubbling
                    const target = /** @type {HTMLInputElement} */ (e.target);
                    const isChecked = target.checked;
                    const originalState = entry.checked;

                    entry.checked = isChecked;

                    try {
                        await apiClient.updateJournalEntry(entry.id, entry);
                    } catch (error) {
                        console.error('Update failed', error);
                        entry.checked = originalState;
                        target.checked = originalState;
                        alert('Fehler beim Speichern des Status.');
                    }
                });

                contentWrapper.appendChild(checkbox);
                contentWrapper.appendChild(content);
                bubble.appendChild(contentWrapper);
            } else {
                bubble.appendChild(content);
            }

            const timestamp = document.createElement('span');
            timestamp.classList.add('timestamp');
            timestamp.textContent = formatTimeRange(
                entry.start_datetime,
                entry.stop_datetime,
                entry.zoneId
            );

            const footer = document.createElement('div');
            footer.classList.add('entry-footer');

            const categoriesDiv = document.createElement('div');
            categoriesDiv.classList.add('entry-categories');

            if (entry.categoryIds && entry.categoryIds.length > 0) {
                const otherCategories = entry.categoryIds
                    .filter(id => id !== currentCategoryId)
                    .map(id => {
                        const cat = allCategories.find(c => c.id === id);
                        return cat ? cat.category : '';
                    })
                    .filter(name => name !== '');

                if (otherCategories.length > 0) {
                    otherCategories.forEach(catName => {
                        const tag = document.createElement('span');
                        tag.classList.add('entry-category-tag');
                        tag.textContent = catName;
                        categoriesDiv.appendChild(tag);
                    });
                }
            }

            footer.appendChild(categoriesDiv);
            footer.appendChild(timestamp);

            bubble.appendChild(footer);

            journalEntriesContainer.appendChild(bubble);
        });
    }

    // Event Listener für das Dropdown
    categorySelect.addEventListener('change', () => {
        const raw = categorySelect.value;
        const parsedId = raw ? parseInt(raw, 10) : null;
        fetchJournalEntries(parsedId);
    });

    // Event Listener für Neuer Eintrag Button
    newEntryButton.addEventListener('click', () => openEditModal(null));

    // Event Listener für Settings Toggle
    settingsToggle.addEventListener('click', () => {
        settingsPanel.classList.toggle('open');
    });

    // Event Listener für API Host Änderung
    refreshApiButton.addEventListener('click', () => {
        const newHost = apiUrlInput.value.trim();
        const parsedPageSize = parseInt(pageSizeInput.value, 10);

        if (newHost && !Number.isNaN(parsedPageSize) && parsedPageSize > 0) {
            apiHost = newHost;
            apiClient.setBaseUrl(`http://${apiHost}:8080`);
            pageSize = clampPageSize(parsedPageSize);
            pageSizeInput.value = String(pageSize);

            localStorage.setItem('voicejournal_api_host', apiHost);
            localStorage.setItem('voicejournal_page_size', String(pageSize));

            // UI zurücksetzen und neu laden
            journalEntriesContainer.innerHTML = '';
            journalEntriesContainer.appendChild(scrollSentinel);
            scrollSentinel.textContent = '';
            categorySelect.innerHTML = '<option value="">--Bitte eine Kategorie auswählen--</option>';
            currentCategoryId = null;

            fetchCategories();
        }
    });

    /**
     * Modal Funktionen
     * @param {JournalEntryDto | null} entry
     */
    function openEditModal(entry) {
        currentEditingEntry = entry;

        if (entry) {
            modalTitle.textContent = 'Eintrag bearbeiten';
            deleteButton.style.display = 'block';
            editContentTextarea.value = entry.content;

            // Datum im gespeicherten zoneId des Eintrags formatieren (YYYY-MM-DDTHH:mm)
            editDateInput.value = formatDateTimeLocal(entry.start_datetime, entry.zoneId);

            // Enddatum formatieren
            if (entry.stop_datetime != null) {
                editEndDateInput.value = formatDateTimeLocal(entry.stop_datetime, entry.zoneId);
            } else {
                editEndDateInput.value = '';
            }
        } else {
            modalTitle.textContent = 'Neuer Eintrag';
            deleteButton.style.display = 'none';
            editContentTextarea.value = '';

            // Aktuelles Datum setzen
            editDateInput.value = formatDateTimeLocal(Date.now(), getBrowserZoneId());
            editEndDateInput.value = '';
        }

        // Kategorien Checkboxen generieren
        editCategoriesContainer.innerHTML = '';
        const currentSelectedCategoryId = parseInt(categorySelect.value, 10);

        allCategories.forEach(category => {
            const label = document.createElement('label');
            label.classList.add('category-checkbox');

            const checkbox = document.createElement('input');
            checkbox.type = 'checkbox';
            checkbox.value = String(category.id);

            if (entry) {
                // Bearbeiten: Prüfen ob die Kategorie dem Eintrag zugeordnet ist
                if (entry.categoryIds && entry.categoryIds.includes(category.id)) {
                    checkbox.checked = true;
                }
            } else {
                // Neu: Aktuell ausgewählte Kategorie vorselektieren
                if (category.id === currentSelectedCategoryId) {
                    checkbox.checked = true;
                }
            }

            label.appendChild(checkbox);
            label.appendChild(document.createTextNode(' ' + category.category));
            editCategoriesContainer.appendChild(label);
        });

        modal.style.display = 'block';
    }

    function closeEditModal() {
        modal.style.display = 'none';
        currentEditingEntry = null;
    }

    async function deleteEntry() {
        if (!currentEditingEntry) return;

        try {
            await apiClient.deleteJournalEntry(currentEditingEntry.id);

            // Remove entry from DOM
            const entryElement = document.querySelector(`.journal-entry[data-id="${currentEditingEntry.id}"]`);
            if (entryElement) {
                entryElement.remove();
            }

            closeEditModal();
        } catch (error) {
            console.error('Fehler beim Löschen des Eintrags:', error);
            alert('Fehler beim Löschen des Eintrags.');
        }
    }

    async function saveEntry() {
        const newContent = editContentTextarea.value;
        const targetZoneId = currentEditingEntry
            ? (currentEditingEntry.zoneId ?? null)
            : getBrowserZoneId();

        const newStartDatetime = parseDateTimeLocalInZone(editDateInput.value, targetZoneId);

        const newEndDateString = editEndDateInput.value;
        /** @type {number | null} */
        let newStopDatetime = null;
        if (newEndDateString) {
            newStopDatetime = parseDateTimeLocalInZone(newEndDateString, targetZoneId);
        }

        // Ausgewählte Kategorien sammeln
        const selectedCategoryIds = Array.from(
            editCategoriesContainer.querySelectorAll('input[type="checkbox"]:checked')
        ).map(cb => parseInt(/** @type {HTMLInputElement} */ (cb).value, 10));

        try {
            if (currentEditingEntry) {
                /** @type {JournalEntryDto} */
                const updatedEntry = {
                    ...currentEditingEntry,
                    content: newContent,
                    start_datetime: newStartDatetime,
                    stop_datetime: newStopDatetime,
                    categoryIds: selectedCategoryIds,
                    zoneId: targetZoneId
                };

                await apiClient.updateJournalEntry(currentEditingEntry.id, updatedEntry);

                // Update existing entry in DOM without reloading list
                const entryElement = document.querySelector(`.journal-entry[data-id="${currentEditingEntry.id}"]`);
                if (entryElement) {
                    const contentElement = entryElement.querySelector('.entry-content');
                    const timestampElement = entryElement.querySelector('.timestamp');

                    if (contentElement) contentElement.textContent = updatedEntry.content;
                    if (timestampElement) {
                        timestampElement.textContent = formatTimeRange(
                            updatedEntry.start_datetime,
                            updatedEntry.stop_datetime,
                            updatedEntry.zoneId
                        );
                    }

                    // Update categories display
                    const existingCategoriesDiv = entryElement.querySelector('.entry-categories');
                    if (existingCategoriesDiv) {
                        existingCategoriesDiv.remove();
                    }

                    if (updatedEntry.categoryIds && updatedEntry.categoryIds.length > 0) {
                        const otherCategories = updatedEntry.categoryIds
                            .filter(id => id !== currentCategoryId)
                            .map(id => {
                                const cat = allCategories.find(c => c.id === id);
                                return cat ? cat.category : '';
                            })
                            .filter(name => name !== '');

                        if (otherCategories.length > 0) {
                            const newCategoriesDiv = document.createElement('div');
                            newCategoriesDiv.classList.add('entry-categories');

                            otherCategories.forEach(catName => {
                                const tag = document.createElement('span');
                                tag.classList.add('entry-category-tag');
                                tag.textContent = catName;
                                newCategoriesDiv.appendChild(tag);
                            });

                            const footer = entryElement.querySelector('.entry-footer');
                            if (footer) {
                                const timestampEl = footer.querySelector('.timestamp');
                                if (timestampEl) {
                                    footer.insertBefore(newCategoriesDiv, timestampEl);
                                } else {
                                    footer.appendChild(newCategoriesDiv);
                                }
                            } else {
                                entryElement.appendChild(newCategoriesDiv);
                            }
                        }
                    }

                    // Update the local object so next openEditModal has correct data
                    Object.assign(currentEditingEntry, updatedEntry);
                }
            } else {
                /** @type {CreateJournalEntryDto} */
                const newEntryPayload = {
                    content: newContent,
                    start_datetime: newStartDatetime,
                    stop_datetime: newStopDatetime,
                    hasImage: false,
                    checked: false,
                    categoryIds: selectedCategoryIds,
                    zoneId: targetZoneId
                };

                await apiClient.createJournalEntry(newEntryPayload);

                // UI aktualisieren (Liste neu laden)
                const parsedId = categorySelect.value ? parseInt(categorySelect.value, 10) : null;
                fetchJournalEntries(parsedId);
            }

            closeEditModal();
        } catch (error) {
            console.error('Fehler beim Speichern des Eintrags:', error);
            alert('Fehler beim Speichern des Eintrags.');
        }
    }

    // Event Listener für Modal
    if (closeButton) {
        closeButton.addEventListener('click', closeEditModal);
    }
    cancelButton.addEventListener('click', closeEditModal);
    saveButton.addEventListener('click', saveEntry);
    deleteButton.addEventListener('click', deleteEntry);
    window.addEventListener('click', (event) => {
        if (event.target === modal) {
            closeEditModal();
        }
    });

    // Initiales Laden der Kategorien
    fetchCategories();
}

if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', initVoiceJournalApp);
} else {
    initVoiceJournalApp();
}
