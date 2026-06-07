import {expect, test} from '@playwright/test';

const ragStatus = {
    enabled: true,
    indexed: true,
    corpusRoot: '/repo/docs',
    documentCount: 12,
    chunkCount: 48,
    retrievalMode: 'lexical',
    retrievalStore: 'in-memory',
    vectorStore: 'qdrant',
    retrievalTargets: [
        retrievalTarget('lexical:in-memory', 'Lexical', true, 'Ready. Uses lexical retrieval.'),
        retrievalTarget('vector:in-memory', 'Vector - In Memory', true, 'Ready. Uses in-memory vector retrieval.'),
        retrievalTarget('vector:qdrant', 'Vector - Qdrant', true, 'Ready. Uses Qdrant vector retrieval.')
    ]
};

test('rag comparison cards stay visually compact', async ({page}) => {
    await mockFrontendApis(page);

    await page.goto('/');
    await page.getByRole('tab', {name: 'RAG'}).click();
    await page.getByPlaceholder(/Ask a question about the project docs/i).fill('How are sessions persisted?');
    await page.getByRole('button', {name: /Compare Retrieval Targets/i}).click();

    const comparison = page.getByRole('region', {name: /RAG retrieval comparison/i});
    await expect(comparison).toBeVisible();
    await expect(comparison.getByRole('heading', {name: 'Lexical'})).toBeVisible();
    await expect(comparison.getByRole('heading', {name: 'Vector - In Memory'})).toBeVisible();
    await expect(comparison.getByRole('heading', {name: 'Vector - Qdrant'})).toBeVisible();

    const lexicalCard = comparison.locator('.rag-comparison-card').first();
    const lexicalTitle = await lexicalCard.getByRole('heading', {name: 'Lexical'}).boundingBox();
    const lexicalAnswerTitle = await lexicalCard.getByRole('heading', {name: 'Answer'}).boundingBox();
    expect(verticalGapBetween(lexicalTitle, lexicalAnswerTitle)).toBeLessThanOrEqual(32);

    await expect(comparison).toHaveScreenshot('rag-comparison.png', {
        animations: 'disabled',
        maxDiffPixelRatio: 0.06
    });
});

function verticalGapBetween(firstBox, secondBox) {
    if (!firstBox || !secondBox) {
        throw new Error('Could not measure RAG comparison card spacing.');
    }
    return secondBox.y - (firstBox.y + firstBox.height);
}

async function mockFrontendApis(page) {
    await page.route('**/api/rag/status', async (route) => {
        await route.fulfill({json: ragStatus});
    });

    await page.route('**/api/models**', async (route) => {
        await route.fulfill({
            json: {
                provider: 'ollama',
                defaultProvider: 'ollama',
                providers: ['ollama'],
                defaultModel: 'llama3:8b',
                models: ['llama3:8b']
            }
        });
    });

    await page.route('**/api/sessions**', async (route) => {
        await route.fulfill({json: []});
    });

    await page.route('**/api/rag/query', async (route) => {
        const body = route.request().postDataJSON();
        await route.fulfill({
            json: {
                answer: answerFor(body.retrievalMode, body.vectorStore),
                provider: 'ollama',
                model: 'llama3:8b',
                sessionId: null,
                sources: [
                    {
                        sourcePath: 'docs/architecture.md',
                        title: 'Architecture',
                        excerpt: 'Conversation history is stored as backend-managed local JSON session files.',
                        score: body.retrievalMode === 'lexical' ? 0.91 : 0.86
                    }
                ],
                metadata: {
                    provider: 'ollama',
                    modelId: 'llama3:8b'
                },
                ragRetrieval: {
                    retrievalMode: body.retrievalMode,
                    vectorStore: body.vectorStore,
                    retrievalTarget: `${body.retrievalMode}:${body.vectorStore}`
                },
                ragTiming: {
                    retrievalDurationMs: body.retrievalMode === 'lexical' ? 18 : 42,
                    providerDurationMs: 520,
                    totalDurationMs: body.retrievalMode === 'lexical' ? 590 : 640
                }
            }
        });
    });
}

function retrievalTarget(value, label, available, message) {
    return {
        value,
        label,
        available,
        ready: available,
        message
    };
}

function answerFor(retrievalMode, vectorStore) {
    if (retrievalMode === 'lexical') {
        return 'Lexical retrieval found the session persistence documentation by exact keyword match.';
    }
    if (vectorStore === 'qdrant') {
        return 'Qdrant vector retrieval found semantically similar documentation about persisted chat sessions.';
    }
    return 'In-memory vector retrieval found semantically similar documentation about local session history.';
}
