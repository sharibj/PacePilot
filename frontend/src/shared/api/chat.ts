import { api } from './client';

export interface ChatReply {
  reply: string;
}

export const sendChat = (message: string) => api.post<ChatReply>('/chat', { message });
