import {useEffect,useState} from 'react';
import {useMutation,useQuery,useQueryClient} from '@tanstack/react-query';
import {api,errorMessage} from './api';

type Attachment={id:string;questionId:string;mimeType:string;byteSize:number;createdAt:string};

function FilePreview({file}:{file:Attachment}){
 const[url,setUrl]=useState<string>();
 useEffect(()=>{let active=true;let objectUrl:string|undefined;
  api.get<Blob>(`/files/${file.id}/content`,{responseType:'blob'}).then(response=>{
   objectUrl=URL.createObjectURL(response.data);if(active)setUrl(objectUrl);
  }).catch(()=>{});
  return()=>{active=false;if(objectUrl)URL.revokeObjectURL(objectUrl)};
 },[file.id]);
 if(!url)return <span className="muted">Đang tải tệp…</span>;
 return file.mimeType.startsWith('image/')?<a href={url} target="_blank" rel="noreferrer"><img className="attachment-thumb" src={url} alt="Ảnh bài làm"/></a>:<a href={url} target="_blank" rel="noreferrer" className="text-action">Xem PDF</a>;
}

export function AttachmentGallery({listPath,uploadPath,disabled=false}:{listPath:string;uploadPath?:string;disabled?:boolean}){
 const qc=useQueryClient(),query=useQuery({queryKey:['attachments',listPath],queryFn:async()=>(await api.get<Attachment[]>(listPath)).data});
 const upload=useMutation({mutationFn:(file:File)=>{const data=new FormData();data.append('file',file);return api.post(uploadPath!,data)},onSuccess:()=>qc.invalidateQueries({queryKey:['attachments',listPath]})});
 return <div className="attachment-gallery"><strong>Ảnh hoặc PDF bài làm</strong>
  {query.isPending?<p className="muted">Đang tải tệp…</p>:query.isError?<p className="alert">{errorMessage(query.error)}</p>:query.data.length?<div className="attachment-list">{query.data.map(file=><FilePreview key={file.id} file={file}/>)}</div>:<p className="muted">Chưa có tệp đính kèm.</p>}
  {uploadPath&&!disabled&&<label className="attachment-upload">Tải lên tối đa 5 tệp JPG, PNG, WEBP hoặc PDF, mỗi tệp tối đa 10 MB
   <input type="file" accept="image/jpeg,image/png,image/webp,application/pdf" disabled={upload.isPending||!!query.data&&query.data.length>=5} onChange={e=>{const file=e.target.files?.[0];if(file)upload.mutate(file);e.target.value=''}}/>
  </label>}
  {upload.isPending&&<p>Đang tải lên…</p>}{upload.isError&&<p className="alert">{errorMessage(upload.error)}</p>}
 </div>;
}
