import { api } from './client';

export interface Item {
  id: number;
  name: string;
  createdAt: string;
}

export const listItems = () => api.get<Item[]>('/items');

export const createItem = (name: string) => api.post<Item>('/items', { name });

export const deleteItem = (id: number) => api.delete<void>(`/items/${id}`);
