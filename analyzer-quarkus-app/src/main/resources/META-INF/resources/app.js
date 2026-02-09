// File Schema Analyzer - Web UI JavaScript

let currentSchema = null;
let parserOptionsCache = {};

// Initialize the application
document.addEventListener('DOMContentLoaded', () => {
    initializeFileInput();
    initializeForm();
    loadSupportedTypes();
    setupFileTypeListener();
});

// File input handling
function initializeFileInput() {
    const fileInput = document.getElementById('fileInput');
    const fileInputLabel = document.getElementById('fileInputLabel');

    fileInput.addEventListener('change', (e) => {
        const file = e.target.files[0];
        if (file) {
            fileInputLabel.classList.add('has-file');
            fileInputLabel.innerHTML = `
                <strong>📎 ${file.name}</strong>
                <div style="font-size: 12px; margin-top: 5px; color: #666;">
                    ${formatFileSize(file.size)} - Click to change
                </div>
            `;

            // Update file type badge
            updateFileTypeBadge(file.name);

            // Auto-detect file type
            autoDetectFileType(file.name);
        }
    });

    // Drag and drop support
    const dropZone = fileInputLabel;

    dropZone.addEventListener('dragover', (e) => {
        e.preventDefault();
        dropZone.style.borderColor = '#667eea';
        dropZone.style.background = '#e7f3ff';
    });

    dropZone.addEventListener('dragleave', () => {
        dropZone.style.borderColor = '#ddd';
        dropZone.style.background = '#f8f9fa';
    });

    dropZone.addEventListener('drop', (e) => {
        e.preventDefault();
        dropZone.style.borderColor = '#ddd';
        dropZone.style.background = '#f8f9fa';

        const files = e.dataTransfer.files;
        if (files.length > 0) {
            fileInput.files = files;
            fileInput.dispatchEvent(new Event('change'));
        }
    });
}

// Form submission
function initializeForm() {
    const form = document.getElementById('analyzerForm');

    form.addEventListener('submit', async (e) => {
        e.preventDefault();
        await analyzeFile();
    });
}

// Analyze file
async function analyzeFile() {
    const fileInput = document.getElementById('fileInput');
    const file = fileInput.files[0];

    if (!file) {
        showError('Please select a file');
        return;
    }

    // Show loading
    showLoading();
    clearStatus();
    hideOutput();

    // Prepare form data
    const formData = new FormData();
    formData.append('file', file);

    const schemaName = document.getElementById('schemaName').value;
    if (schemaName) {
        formData.append('schemaName', schemaName);
    }

    const fileType = document.getElementById('fileType').value;
    if (fileType) {
        formData.append('fileType', fileType);
    }

    formData.append('detectArrays', document.getElementById('detectArrays').checked);
    formData.append('optimizeForBeanIO', document.getElementById('optimizeForBeanIO').checked);

    // Add parser options
    const parserOptions = getParserOptions();
    if (Object.keys(parserOptions).length > 0) {
        formData.append('parserOptions', JSON.stringify(parserOptions));
    }

    try {
        const response = await fetch('/api/analyzer/analyze-file', {
            method: 'POST',
            body: formData
        });

        const data = await response.json();

        if (response.ok) {
            // Success
            currentSchema = data.jsonSchema;
            displaySchema(data.jsonSchema);
            showSuccess('Schema generated successfully!');

            // Update result info
            const resultInfo = document.getElementById('resultInfo');
            resultInfo.textContent = `${data.fileType || 'Unknown'} format`;
            resultInfo.style.display = 'inline-block';
        } else {
            // Error from API
            showError(`Error: ${data.message || 'Unknown error'}`);
            hideOutput();
        }
    } catch (error) {
        console.error('Analysis error:', error);
        showError(`Failed to analyze file: ${error.message}`);
        hideOutput();
    } finally {
        hideLoading();
    }
}

// Display schema in output area
function displaySchema(schema) {
    const outputContainer = document.getElementById('outputContainer');
    const downloadBtn = document.getElementById('downloadBtn');

    outputContainer.classList.remove('empty');

    // Pretty print JSON with syntax highlighting
    const formattedJson = JSON.stringify(schema, null, 2);
    outputContainer.innerHTML = `<pre>${syntaxHighlight(formattedJson)}</pre>`;

    downloadBtn.style.display = 'block';
}

// Syntax highlighting for JSON
function syntaxHighlight(json) {
    json = json.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
    return json.replace(/("(\\u[a-zA-Z0-9]{4}|\\[^u]|[^\\"])*"(\s*:)?|\b(true|false|null)\b|-?\d+(?:\.\d*)?(?:[eE][+\-]?\d+)?)/g, (match) => {
        let cls = 'number';
        if (/^"/.test(match)) {
            if (/:$/.test(match)) {
                cls = 'key';
                return `<span style="color: #881391;">${match}</span>`;
            } else {
                cls = 'string';
                return `<span style="color: #1A1AA6;">${match}</span>`;
            }
        } else if (/true|false/.test(match)) {
            cls = 'boolean';
            return `<span style="color: #0000FF;">${match}</span>`;
        } else if (/null/.test(match)) {
            cls = 'null';
            return `<span style="color: #808080;">${match}</span>`;
        }
        return `<span style="color: #09885A;">${match}</span>`;
    });
}

// Download JSON Schema
document.getElementById('downloadBtn').addEventListener('click', () => {
    if (!currentSchema) return;

    const schemaName = document.getElementById('schemaName').value || 'schema';
    const fileName = `${schemaName}.json`;

    const blob = new Blob([JSON.stringify(currentSchema, null, 2)], {
        type: 'application/json'
    });

    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = fileName;
    a.click();

    URL.revokeObjectURL(url);

    showSuccess(`Schema downloaded as ${fileName}`);
});

// Load supported file types
async function loadSupportedTypes() {
    try {
        const response = await fetch('/api/analyzer/supported-types');
        const data = await response.json();

        console.log('Supported types:', data);
    } catch (error) {
        console.error('Failed to load supported types:', error);
    }
}

// Auto-detect file type from extension
function autoDetectFileType(fileName) {
    const extension = fileName.split('.').pop().toLowerCase();
    const fileTypeSelect = document.getElementById('fileType');

    const typeMap = {
        'csv': 'CSV',
        'json': 'JSON',
        'txt': 'FIXED_LENGTH',
        'dat': 'FIXED_LENGTH'
    };

    if (typeMap[extension]) {
        fileTypeSelect.value = typeMap[extension];
        loadParserOptions(typeMap[extension]);
    }
}

// Setup file type change listener
function setupFileTypeListener() {
    const fileTypeSelect = document.getElementById('fileType');

    fileTypeSelect.addEventListener('change', (e) => {
        const fileType = e.target.value;
        if (fileType) {
            loadParserOptions(fileType);
        } else {
            clearParserOptions();
        }
    });
}

// Load parser options for file type
async function loadParserOptions(fileType) {
    if (!fileType) {
        clearParserOptions();
        return;
    }

    // Check cache
    if (parserOptionsCache[fileType]) {
        displayParserOptions(fileType, parserOptionsCache[fileType]);
        return;
    }

    try {
        const response = await fetch(`/api/analyzer/parser-options/${fileType}`);
        const data = await response.json();

        if (response.ok && data.options) {
            parserOptionsCache[fileType] = data.options;
            displayParserOptions(fileType, data.options);
        }
    } catch (error) {
        console.error('Failed to load parser options:', error);
    }
}

// Display parser options
function displayParserOptions(fileType, options) {
    const container = document.getElementById('parserOptionsContainer');

    if (!options || Object.keys(options).length === 0) {
        container.innerHTML = '';
        return;
    }

    let html = '<div class="parser-options">';
    html += '<h4>Parser Options</h4>';

    for (const [key, description] of Object.entries(options)) {
        const inputId = `parser_${key}`;
        const defaultValue = getDefaultValue(fileType, key);

        html += `
            <div class="parser-option">
                <label for="${inputId}">${key}</label>
                <input type="text" id="${inputId}"
                       placeholder="${description}"
                       value="${defaultValue}"
                       data-parser-key="${key}">
            </div>
        `;
    }

    html += '</div>';
    container.innerHTML = html;
}

// Get default values for common parser options
function getDefaultValue(fileType, key) {
    const defaults = {
        'CSV': {
            'delimiter': ';',
            'hasHeader': 'true'
        },
        'FIXED_LENGTH': {
            'lineLength': ''
        }
    };

    return defaults[fileType]?.[key] || '';
}

// Get parser options from form
function getParserOptions() {
    const options = {};
    const inputs = document.querySelectorAll('[data-parser-key]');

    inputs.forEach(input => {
        const key = input.dataset.parserKey;
        const value = input.value.trim();
        if (value) {
            options[key] = value;
        }
    });

    return options;
}

// Clear parser options
function clearParserOptions() {
    document.getElementById('parserOptionsContainer').innerHTML = '';
}

// UI Helper Functions
function showLoading() {
    document.getElementById('loadingSpinner').classList.add('active');
    document.getElementById('analyzeBtn').disabled = true;
}

function hideLoading() {
    document.getElementById('loadingSpinner').classList.remove('active');
    document.getElementById('analyzeBtn').disabled = false;
}

function showError(message) {
    const statusMessage = document.getElementById('statusMessage');
    statusMessage.innerHTML = `<div class="error">${message}</div>`;
}

function showSuccess(message) {
    const statusMessage = document.getElementById('statusMessage');
    statusMessage.innerHTML = `<div class="success">${message}</div>`;

    // Auto-hide after 3 seconds
    setTimeout(() => {
        statusMessage.innerHTML = '';
    }, 3000);
}

function clearStatus() {
    document.getElementById('statusMessage').innerHTML = '';
}

function hideOutput() {
    const outputContainer = document.getElementById('outputContainer');
    const downloadBtn = document.getElementById('downloadBtn');

    outputContainer.classList.add('empty');
    outputContainer.innerHTML = '<p>JSON Schema will appear here after analysis</p>';
    downloadBtn.style.display = 'none';

    const resultInfo = document.getElementById('resultInfo');
    resultInfo.style.display = 'none';
}

function updateFileTypeBadge(fileName) {
    const badge = document.getElementById('fileTypeBadge');
    const extension = fileName.split('.').pop().toUpperCase();
    badge.textContent = extension;
}

function formatFileSize(bytes) {
    if (bytes === 0) return '0 Bytes';
    const k = 1024;
    const sizes = ['Bytes', 'KB', 'MB', 'GB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return Math.round(bytes / Math.pow(k, i) * 100) / 100 + ' ' + sizes[i];
}
