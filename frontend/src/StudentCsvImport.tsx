import {useState} from 'react';
import {useMutation} from '@tanstack/react-query';
import {X} from 'lucide-react';
import {api,errorMessage} from './api';

type ImportResult={created:number;failed:number;errors:{row:number;message:string}[]};

export function StudentCsvImport({onClose,onImported}:{onClose:()=>void;onImported:()=>void}){
 const[file,setFile]=useState<File>();
 const mutation=useMutation({mutationFn:async()=>{
  const data=new FormData();data.append('file',file!);
  return (await api.post<ImportResult>('/admin/students/import',data)).data;
 },onSuccess:onImported});
 function template(){
  const blob=new Blob(['email,fullName,phone,dateOfBirth,parentEmail\n'],{type:'text/csv;charset=utf-8'});
  const url=URL.createObjectURL(blob);const anchor=document.createElement('a');anchor.href=url;anchor.download='edutrack-students.csv';anchor.click();URL.revokeObjectURL(url);
 }
 return <div className="modal-backdrop" onMouseDown={e=>{if(e.target===e.currentTarget)onClose()}}><div className="modal" role="dialog" aria-modal="true" aria-label="Import học sinh"><div className="modal-head"><h2>Import học sinh từ CSV hoặc Excel</h2><button className="icon-button" onClick={onClose} aria-label="Đóng"><X size={20}/></button></div><p className="muted">CSV UTF-8 hoặc Excel XLSX/XLS, tối đa 500 học sinh hoặc 2 MB. Cột bắt buộc: email, fullName. Ngày sinh theo YYYY-MM-DD. Tài khoản được gửi lời mời kích hoạt.</p><button className="text-action" type="button" onClick={template}>Tải mẫu CSV</button><form onSubmit={e=>{e.preventDefault();if(file)mutation.mutate()}}><label>Chọn file CSV hoặc Excel<input type="file" accept=".csv,.xlsx,.xls,text/csv,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet,application/vnd.ms-excel" required onChange={e=>setFile(e.target.files?.[0])}/></label>{mutation.isError&&<div className="alert" role="alert">{errorMessage(mutation.error)}</div>}{mutation.data&&<div className="success"><strong>Đã tạo {mutation.data.created} tài khoản.</strong> {mutation.data.failed>0&&<span>{mutation.data.failed} dòng cần sửa.</span>}</div>}{mutation.data?.errors.length?<div className="import-errors">{mutation.data.errors.map(error=><p key={error.row}>Dòng {error.row}: {error.message}</p>)}</div>:null}<div className="modal-actions"><button className="button ghost" type="button" onClick={onClose}>Đóng</button><button className="button primary" type="submit" disabled={!file||mutation.isPending}>{mutation.isPending?'Đang import…':'Import và gửi lời mời'}</button></div></form></div></div>;
}
