const API_BASE = '/api/kb';

export async function addDocument(content, title = '', source = '', importance = 1.0) {
  const response = await fetch(`${API_BASE}/add`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({ content, title, source, importance }),
  });
  return response.json();
}

export async function importFromDir(dirPath) {
  const response = await fetch(`${API_BASE}/import-dir?dir_path=${encodeURIComponent(dirPath)}`, {
    method: 'POST',
  });
  return response.json();
}

export async function searchDocuments(query, top_k = 5, min_similarity = 0.5) {
  const response = await fetch(`${API_BASE}/search`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({ query, top_k, min_similarity }),
  });
  return response.json();
}

export async function qa(query, top_k = 3) {
  const response = await fetch(`${API_BASE}/qa`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({ query, top_k }),
  });
  return response.json();
}

export async function getDocument(entry_id) {
  const response = await fetch(`${API_BASE}/document/${entry_id}`);
  return response.json();
}

export async function deleteDocument(entry_id) {
  const response = await fetch(`${API_BASE}/document/${entry_id}`, {
    method: 'DELETE',
  });
  return response.json();
}

export async function getStats() {
  const response = await fetch(`${API_BASE}/stats`);
  return response.json();
}