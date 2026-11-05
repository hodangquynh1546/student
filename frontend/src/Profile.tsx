import {useMutation} from '@tanstack/react-query';
import {zodResolver} from '@hookform/resolvers/zod';
import {useForm} from 'react-hook-form';
import {useNavigate} from 'react-router-dom';
import {z} from 'zod';
import {api,errorMessage} from './api';
import {useSession} from './session';
import type {User} from './types';

const profileSchema=z.object({
 fullName:z.string().trim().min(2,'Nhập họ tên từ 2 ký tự').max(120),
 phone:z.string().trim().regex(/^$|^\+?[0-9 .()\-]{7,30}$/,'Số điện thoại không hợp lệ'),
 dateOfBirth:z.string(),
});
const passwordSchema=z.object({currentPassword:z.string().min(1,'Nhập mật khẩu hiện tại'),newPassword:z.string().min(12,'Mật khẩu mới cần ít nhất 12 ký tự').max(72)});
type ProfileInput=z.infer<typeof profileSchema>;
type PasswordInput=z.infer<typeof passwordSchema>;

export function Profile(){
 const user=useSession(s=>s.user),updateUser=useSession(s=>s.updateUser),clear=useSession(s=>s.clear),navigate=useNavigate();
 const profile=useForm<ProfileInput>({resolver:zodResolver(profileSchema),defaultValues:{fullName:user?.fullName||'',phone:user?.phone||'',dateOfBirth:user?.dateOfBirth||''}});
 const password=useForm<PasswordInput>({resolver:zodResolver(passwordSchema)});
 const save=useMutation({mutationFn:async(values:ProfileInput)=>(await api.put<User>('/auth/me',{fullName:values.fullName.trim(),phone:values.phone.trim(),dateOfBirth:values.dateOfBirth||null})).data,onSuccess:updateUser});
 const change=useMutation({mutationFn:async(values:PasswordInput)=>api.post('/auth/change-password',values),onSuccess:()=>{clear();navigate('/login')}});
 return <div>
  <div className="page-head"><div><span className="eyebrow">TÀI KHOẢN</span><h1>Hồ sơ cá nhân</h1><p>Cập nhật thông tin liên hệ và bảo vệ tài khoản của bạn.</p></div></div>
  <div className="card-grid">
   <section className="panel"><h2>Thông tin cá nhân</h2><p className="muted">Email: {user?.email}</p>
    <form className="form-grid" onSubmit={profile.handleSubmit(values=>save.mutate(values))}>
     <label className="full">Họ và tên<input {...profile.register('fullName')} autoComplete="name" required/>{profile.formState.errors.fullName&&<small className="field-error">{profile.formState.errors.fullName.message}</small>}</label>
     <label>Số điện thoại<input {...profile.register('phone')} autoComplete="tel"/>{profile.formState.errors.phone&&<small className="field-error">{profile.formState.errors.phone.message}</small>}</label>
     <label>Ngày sinh<input type="date" {...profile.register('dateOfBirth')}/></label>
     {save.isError&&<div className="alert full" role="alert">{errorMessage(save.error)}</div>}
     {save.isSuccess&&<div className="success full" role="status">Đã lưu hồ sơ.</div>}
     <div className="modal-actions"><button className="button primary" type="submit" disabled={save.isPending}>{save.isPending?'Đang lưu…':'Lưu hồ sơ'}</button></div>
    </form>
   </section>
   <section className="panel"><h2>Đổi mật khẩu</h2><p className="muted">Sau khi đổi mật khẩu, bạn cần đăng nhập lại.</p>
    <form className="form-grid" onSubmit={password.handleSubmit(values=>change.mutate(values))}>
     <label className="full">Mật khẩu hiện tại<input type="password" autoComplete="current-password" {...password.register('currentPassword')} required/>{password.formState.errors.currentPassword&&<small className="field-error">{password.formState.errors.currentPassword.message}</small>}</label>
     <label className="full">Mật khẩu mới<input type="password" autoComplete="new-password" {...password.register('newPassword')} required/>{password.formState.errors.newPassword&&<small className="field-error">{password.formState.errors.newPassword.message}</small>}</label>
     {change.isError&&<div className="alert full" role="alert">{errorMessage(change.error)}</div>}
     <div className="modal-actions"><button className="button secondary" type="submit" disabled={change.isPending}>{change.isPending?'Đang cập nhật…':'Đổi mật khẩu'}</button></div>
    </form>
   </section>
  </div>
 </div>;
}
