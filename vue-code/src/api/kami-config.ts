import { request } from '@/utils/request';

export interface KamiConfig {
  id: number;
  xianyuAccountId: number;
  aliasName: string;
  alertEnabled?: number;
  alertThresholdType?: number;
  alertThresholdValue?: number;
  alertEmail?: string;
  totalCount: number;
  usedCount: number;
  availableCount: number;
  createTime: string;
  updateTime: string;
}

export interface KamiItem {
  id: number;
  kamiConfigId: number;
  kamiContent: string;
  status: number;
  orderId: string | null;
  usedTime: string | null;
  sortOrder: number;
  createTime: string;
}

export interface SaveKamiConfigReq {
  id?: number;
  xianyuAccountId: number;
  aliasName?: string;
  alertEnabled?: number;
  alertThresholdType?: number;
  alertThresholdValue?: number;
  alertEmail?: string;
}

export interface QueryKamiItemsReq {
  kamiConfigId: number;
  status?: number;
  keyword?: string;
}

export interface GenerateNewApiRedemptionsReq {
  kamiConfigId: number;
  name: string;
  amountCny: number;
  count: number;
}

export interface GenerateNewApiRedemptionsResp {
  kamiConfigId: number;
  amountCny: number;
  quota: number;
  expiredTime: number;
  generatedCount: number;
  importedCount: number;
  duplicateCount: number;
  codes: string[];
}

export function saveKamiConfig(data: SaveKamiConfigReq) {
  return request<KamiConfig>({
    url: '/kami-config/save',
    method: 'POST',
    data
  });
}

export function getKamiConfigsByAccountId(xianyuAccountId: number) {
  return request<KamiConfig[]>({
    url: '/kami-config/list',
    method: 'POST',
    params: { xianyuAccountId }
  });
}

export function getKamiConfigById(id: number) {
  return request<KamiConfig>({
    url: '/kami-config/detail',
    method: 'POST',
    params: { id }
  });
}

export function deleteKamiConfig(id: number) {
  return request({
    url: '/kami-config/delete',
    method: 'POST',
    params: { id }
  });
}

export function addKamiItem(data: { kamiConfigId: number; kamiContent: string }) {
  return request<KamiItem>({
    url: '/kami-config/item/add',
    method: 'POST',
    data
  });
}

export function batchImportKamiItems(data: { kamiConfigId: number; kamiContents: string }) {
  return request<number>({
    url: '/kami-config/item/batchImport',
    method: 'POST',
    data
  });
}

export function generateNewApiRedemptions(data: GenerateNewApiRedemptionsReq) {
  return request<GenerateNewApiRedemptionsResp>({
    url: '/new-api/redemption/generate',
    method: 'POST',
    data
  });
}

export function getKamiItemsByConfigId(kamiConfigId: number) {
  return request<KamiItem[]>({
    url: '/kami-config/item/list',
    method: 'POST',
    params: { kamiConfigId }
  });
}

export function queryKamiItems(data: QueryKamiItemsReq) {
  return request<KamiItem[]>({
    url: '/kami-config/item/query',
    method: 'POST',
    data
  });
}

export function deleteKamiItem(id: number) {
  return request({
    url: '/kami-config/item/delete',
    method: 'POST',
    params: { id }
  });
}

export function resetKamiItem(id: number) {
  return request({
    url: '/kami-config/item/reset',
    method: 'POST',
    params: { id }
  });
}

export function exportKamiItems(data: { kamiConfigId: number; includeUnused: boolean; includeUsed: boolean }) {
  return request<KamiItem[]>({
    url: '/kami-config/item/export',
    method: 'POST',
    data
  });
}
