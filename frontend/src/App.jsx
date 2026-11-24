import { useEffect, useState } from 'react';
import { createItem, deleteItem, getItems, updateItem } from './api.js';

const emptyForm = { name: '', description: '' };

export default function App() {
  const [items, setItems] = useState([]);
  const [form, setForm] = useState(emptyForm);
  const [editingId, setEditingId] = useState(null);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);

  async function load() {
    try {
      setItems(await getItems());
      setError('');
    } catch (e) {
      setError(e.message);
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    load();
  }, []);

  async function handleSubmit(event) {
    event.preventDefault();
    if (!form.name.trim()) {
      setError('Назва обов\'язкова');
      return;
    }
    try {
      if (editingId === null) {
        await createItem(form);
      } else {
        await updateItem(editingId, form);
      }
      setForm(emptyForm);
      setEditingId(null);
      await load();
    } catch (e) {
      setError(e.message);
    }
  }

  function startEdit(item) {
    setEditingId(item.id);
    setForm({ name: item.name ?? '', description: item.description ?? '' });
  }

  function cancelEdit() {
    setEditingId(null);
    setForm(emptyForm);
  }

  async function handleDelete(id) {
    try {
      await deleteItem(id);
      if (editingId === id) cancelEdit();
      await load();
    } catch (e) {
      setError(e.message);
    }
  }

  return (
    <main className="page">
      <h1>Items</h1>

      <form className="card form" onSubmit={handleSubmit}>
        <h2>{editingId === null ? 'Новий елемент' : `Редагування #${editingId}`}</h2>
        <label>
          Назва
          <input
            value={form.name}
            onChange={(e) => setForm({ ...form, name: e.target.value })}
            placeholder="Наприклад, Book"
          />
        </label>
        <label>
          Опис
          <textarea
            value={form.description}
            onChange={(e) => setForm({ ...form, description: e.target.value })}
            rows={3}
          />
        </label>
        <div className="actions">
          <button type="submit">{editingId === null ? 'Додати' : 'Зберегти'}</button>
          {editingId !== null && (
            <button type="button" className="secondary" onClick={cancelEdit}>
              Скасувати
            </button>
          )}
        </div>
      </form>

      {error && <p className="error">{error}</p>}

      <section className="card">
        <h2>Список ({items.length})</h2>
        {loading ? (
          <p>Завантаження…</p>
        ) : items.length === 0 ? (
          <p className="muted">Поки що немає жодного елемента.</p>
        ) : (
          <table>
            <thead>
              <tr>
                <th>ID</th>
                <th>Назва</th>
                <th>Опис</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {items.map((item) => (
                <tr key={item.id}>
                  <td>{item.id}</td>
                  <td>{item.name}</td>
                  <td>{item.description}</td>
                  <td className="row-actions">
                    <button type="button" className="secondary" onClick={() => startEdit(item)}>
                      Редагувати
                    </button>
                    <button type="button" className="danger" onClick={() => handleDelete(item.id)}>
                      Видалити
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </section>
    </main>
  );
}
