// @ts-check

/**
 * Central REST API client for the Voice Journal Android backend.
 * Types are generated from `../api/openapi.yaml` via `npm run sync-api`.
 *
 * @typedef {import('./api-types.d.ts').components['schemas']['CategoryDto']} CategoryDto
 * @typedef {import('./api-types.d.ts').components['schemas']['JournalEntryDto']} JournalEntryDto
 * @typedef {import('./api-types.d.ts').components['schemas']['CreateJournalEntryDto']} CreateJournalEntryDto
 */

/** Matches `WebServer.DEFAULT_PAGE_SIZE` and `PageSizeParam.default` in `api/openapi.yaml`. */
const DEFAULT_PAGE_SIZE = 10;

/** Matches `WebServer.MAX_PAGE_SIZE` and `PageSizeParam.maximum` in `api/openapi.yaml`. */
const MAX_PAGE_SIZE = 500;

/**
 * Clamps a requested page number to the server's valid range (>= 1).
 * @param {number} page
 * @returns {number}
 */
function clampPage(page) {
    if (!Number.isFinite(page)) return 1;
    return Math.max(1, Math.trunc(page));
}

/**
 * Clamps a requested pageSize to the server's valid range (1..MAX_PAGE_SIZE).
 * @param {number} pageSize
 * @returns {number}
 */
function clampPageSize(pageSize) {
    if (!Number.isFinite(pageSize)) return DEFAULT_PAGE_SIZE;
    return Math.min(Math.max(1, Math.trunc(pageSize)), MAX_PAGE_SIZE);
}

class VoiceJournalApiClient {
    /**
     * @param {string} baseUrl Base URL without trailing slash (e.g. `http://localhost:8080`)
     */
    constructor(baseUrl) {
        /** @private @type {string} */
        this.baseUrl = baseUrl.replace(/\/+$/, '');
    }

    /**
     * @param {string} baseUrl
     */
    setBaseUrl(baseUrl) {
        this.baseUrl = baseUrl.replace(/\/+$/, '');
    }

    /**
     * @returns {string}
     */
    getBaseUrl() {
        return this.baseUrl;
    }

    /**
     * GET /categories
     * @returns {Promise<CategoryDto[]>}
     */
    async getCategories() {
        const response = await fetch(`${this.baseUrl}/categories`);
        if (!response.ok) {
            throw new Error(`HTTP error! status: ${response.status}`);
        }
        return /** @type {Promise<CategoryDto[]>} */ (response.json());
    }

    /**
     * GET /journalentries?page={page}&pageSize={pageSize}
     * @param {number} [page=1]
     * @param {number} [pageSize=DEFAULT_PAGE_SIZE]
     * @returns {Promise<JournalEntryDto[]>}
     */
    async getJournalEntries(page = 1, pageSize = DEFAULT_PAGE_SIZE) {
        const safePage = clampPage(page);
        const safeSize = clampPageSize(pageSize);
        const response = await fetch(
            `${this.baseUrl}/journalentries?page=${safePage}&pageSize=${safeSize}`
        );
        if (!response.ok) {
            throw new Error(`HTTP error! status: ${response.status}`);
        }
        return /** @type {Promise<JournalEntryDto[]>} */ (response.json());
    }

    /**
     * GET /journalentries/category/{id}?page={page}&pageSize={pageSize}
     * @param {number} categoryId
     * @param {number} [page=1]
     * @param {number} [pageSize=DEFAULT_PAGE_SIZE]
     * @returns {Promise<JournalEntryDto[]>}
     */
    async getJournalEntriesByCategory(categoryId, page = 1, pageSize = DEFAULT_PAGE_SIZE) {
        const safePage = clampPage(page);
        const safeSize = clampPageSize(pageSize);
        const response = await fetch(
            `${this.baseUrl}/journalentries/category/${encodeURIComponent(String(categoryId))}?page=${safePage}&pageSize=${safeSize}`
        );
        if (!response.ok) {
            throw new Error(`HTTP error! status: ${response.status}`);
        }
        return /** @type {Promise<JournalEntryDto[]>} */ (response.json());
    }

    /**
     * GET /journalentries/{id}
     * @param {string} id Entry UUID
     * @returns {Promise<JournalEntryDto>}
     */
    async getJournalEntryById(id) {
        const response = await fetch(
            `${this.baseUrl}/journalentries/${encodeURIComponent(id)}`
        );
        if (!response.ok) {
            throw new Error(`HTTP error! status: ${response.status}`);
        }
        return /** @type {Promise<JournalEntryDto>} */ (response.json());
    }

    /**
     * POST /journalentries
     * @param {CreateJournalEntryDto} payload
     * @returns {Promise<JournalEntryDto>}
     */
    async createJournalEntry(payload) {
        const response = await fetch(`${this.baseUrl}/journalentries`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify(payload)
        });
        if (!response.ok) {
            throw new Error(`HTTP error! status: ${response.status}`);
        }
        return /** @type {Promise<JournalEntryDto>} */ (response.json());
    }

    /**
     * PUT /journalentries/{id}
     * @param {string} id Entry UUID
     * @param {JournalEntryDto} payload
     * @returns {Promise<void>}
     */
    async updateJournalEntry(id, payload) {
        const response = await fetch(
            `${this.baseUrl}/journalentries/${encodeURIComponent(id)}`,
            {
                method: 'PUT',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify(payload)
            }
        );
        if (!response.ok) {
            throw new Error(`HTTP error! status: ${response.status}`);
        }
    }

    /**
     * DELETE /journalentries/{id}
     * @param {string} id Entry UUID
     * @returns {Promise<void>}
     */
    async deleteJournalEntry(id) {
        const response = await fetch(
            `${this.baseUrl}/journalentries/${encodeURIComponent(id)}`,
            {
                method: 'DELETE'
            }
        );
        if (!response.ok) {
            throw new Error(`HTTP error! status: ${response.status}`);
        }
    }
}
