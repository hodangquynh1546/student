import axios from 'axios';
import { useSession } from './session';
import type { Session } from './types';
export const api=axios.create({baseURL:'/api/v1',withCredentials:true,timeout:20000});
api.interceptors.request.use(config=>{const token=useSession.getState().accessToken;if(token)config.headers.Authorization=`Bearer ${token}`;return config});
let refreshing:Promise<Session>|undefined;
api.interceptors.response.use(r=>r,async error=>{
 const original=error.config;
 if(error.response?.status===401 && original && !original._retried && !String(original.url).includes('/auth/')){
  original._retried=true;
  try {refreshing??=api.post<Session>('/auth/refresh').then(r=>r.data).finally(()=>{refreshing=undefined});const session=await refreshing;useSession.getState().setSession(session);original.headers.Authorization=`Bearer ${session.accessToken}`;return api(original)}
  catch{useSession.getState().clear();window.location.assign('/login')}
 }
 return Promise.reject(error);
});
export function errorMessage(error:unknown){if(axios.isAxiosError(error)){const d=error.response?.data as {message?:string;detail?:string;error?:string}|undefined;return d?.message||d?.detail||d?.error||(error.response?.status===403?'Bạn không có quyền thực hiện thao tác này.':'Không thể kết nối. Vui lòng thử lại.')}return 'Đã có lỗi xảy ra. Vui lòng thử lại.'}
