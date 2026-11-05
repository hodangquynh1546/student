import {useState} from 'react';
import {useMutation,useQuery,useQueryClient} from '@tanstack/react-query';
import {Link} from 'react-router-dom';
import {api,errorMessage} from './api';
import type {Child,Classroom,Student,User} from './types';

type Period='ALL'|'WEEK'|'MONTH'|'SEMESTER';
type RankingRow={studentId:string;studentName:string;rank:number;assignedCount:number;completedCount:number;averagePercent:number;completionPercent:number;progressPercent:number;rankingScore:number};
type TeacherRanking={classroomId:string;period:Period;cohortSize:number;rows:RankingRow[];formula:string};
type PrivateRanking=Omit<RankingRow,'studentId'|'studentName'> & {classroomId:string;period:Period;cohortSize:number;formula:string};
type Topic={name:string;questionCount:number;earnedPoints:number;possiblePoints:number;percentage:number|null};
type Report={id:string;studentId:string;period:'WEEK'|'MONTH';start:string;end:string;assignedCount:number;completedCount:number;completionRate:number|null;publishedResultCount:number;averagePercent:number|null;progressPoints:number|null;topics:Topic[];generatedAt:string};
type Notice={id:string;type:string;title:string;body:string;createdAt:string;readAt:string|null};
type NoticePage={content:Notice[];totalPages:number;totalElements:number;number:number};

function State({text,error}:{text:string;error?:unknown}){return <div className={error?'alert':'panel empty'} role={error?'alert':undefined}>{error?errorMessage(error):text}</div>}
function pct(n:number|null|undefined){return n==null?'Chưa có dữ liệu':`${Number(n).toFixed(1)}%`}
function title(eyebrow:string,heading:string,description:string){return <div className="page-head"><div><span className="eyebrow">{eyebrow}</span><h1>{heading}</h1><p>{description}</p></div></div>}

export function Rankings({user}:{user:User}){
 const isTeacher=user.role==='ADMIN_TEACHER',isParent=user.role==='PARENT';
 const [childId,setChildId]=useState(''),[classId,setClassId]=useState(''),[period,setPeriod]=useState<Period>('ALL');
 const children=useQuery({queryKey:['children'],queryFn:async()=>(await api.get<Child[]>('/parent/children')).data,enabled:isParent});
 const effectiveChild=children.data?.find(c=>c.id===childId)?.id||children.data?.[0]?.id;
 const classes=useQuery({queryKey:['ranking-classes',user.role,effectiveChild],queryFn:async()=>{
   const url=isTeacher?'/admin/classrooms':isParent?`/parent/children/${effectiveChild}/classrooms`:'/student/classrooms';
   return (await api.get<Classroom[]>(url)).data;
 },enabled:!isParent||!!effectiveChild});
 const effectiveClass=classes.data?.find(c=>c.id===classId)?.id||classes.data?.[0]?.id;
 const ranking=useQuery({queryKey:['ranking',user.role,effectiveChild,effectiveClass,period],queryFn:async()=>{
   const url=isTeacher?`/admin/classrooms/${effectiveClass}/ranking`:isParent?`/parent/children/${effectiveChild}/classrooms/${effectiveClass}/ranking`:`/student/classrooms/${effectiveClass}/ranking`;
   return (await api.get<TeacherRanking|PrivateRanking>(url,{params:{period}})).data;
 },enabled:!!effectiveClass});
 return <>{title('THÀNH TÍCH','Xếp hạng học tập','Điểm xếp hạng dùng kết quả đã công bố, tỷ lệ hoàn thành và mức tiến bộ.')}
 <div className="panel"><div className="form-grid">
 {isParent&&<label>Con của tôi<select value={effectiveChild||''} onChange={e=>{setChildId(e.target.value);setClassId('')}}>{children.data?.map(c=><option key={c.id} value={c.id}>{c.fullName}</option>)}</select></label>}
 <label>Lớp học<select value={effectiveClass||''} onChange={e=>setClassId(e.target.value)} disabled={!classes.data?.length}>{classes.data?.map(c=><option key={c.id} value={c.id}>{c.name} ({c.code})</option>)}</select></label>
 <label>Khoảng thời gian<select value={period} onChange={e=>setPeriod(e.target.value as Period)}><option value="ALL">Toàn bộ</option><option value="WEEK">Tuần này</option><option value="MONTH">Tháng này</option><option value="SEMESTER">Học kỳ</option></select></label>
 </div></div>
 {children.isError?<State text="" error={children.error}/>:classes.isPending?<State text="Đang tải lớp học…"/>:classes.isError?<State text="" error={classes.error}/>:!classes.data?.length?<State text="Chưa có lớp học để xem xếp hạng."/>:ranking.isPending?<State text="Đang tính xếp hạng…"/>:ranking.isError?<State text="" error={ranking.error}/>:<div className="panel"><h2>{isTeacher?'Bảng xếp hạng lớp':'Thứ hạng của bạn'}</h2><p className="muted">{ranking.data.cohortSize} học sinh · {ranking.data.formula}</p>
 {isTeacher?((ranking.data as TeacherRanking).rows.length?<div className="table-wrap"><table><thead><tr><th>Hạng</th><th>Học sinh</th><th>Điểm TB</th><th>Hoàn thành</th><th>Tiến bộ</th><th>Điểm xếp hạng</th></tr></thead><tbody>{(ranking.data as TeacherRanking).rows.map(row=><tr key={row.studentId}><td>#{row.rank}</td><td>{row.studentName}</td><td>{pct(row.averagePercent)}</td><td>{row.completedCount}/{row.assignedCount} · {pct(row.completionPercent)}</td><td>{pct(row.progressPercent)}</td><td><strong>{Number(row.rankingScore).toFixed(1)}</strong></td></tr>)}</tbody></table></div>:<State text="Chưa có dữ liệu xếp hạng trong kỳ này."/>):<div className="overview-grid analytics-metrics"><div className="metric-card"><strong>#{(ranking.data as PrivateRanking).rank || '—'}</strong><span>Thứ hạng / {ranking.data.cohortSize} học sinh</span></div><div className="metric-card"><strong>{pct((ranking.data as PrivateRanking).averagePercent)}</strong><span>Điểm trung bình</span></div><div className="metric-card"><strong>{pct((ranking.data as PrivateRanking).completionPercent)}</strong><span>Tỷ lệ hoàn thành</span></div><div className="metric-card"><strong>{pct((ranking.data as PrivateRanking).progressPercent)}</strong><span>Mức tiến bộ</span></div></div>}</div>}
 </>;
}

function defaultStart(period:'WEEK'|'MONTH'){
 const d=new Date();d.setUTCHours(0,0,0,0);
 if(period==='WEEK'){const day=(d.getUTCDay()+6)%7;d.setUTCDate(d.getUTCDate()-day-7)}
 else d.setUTCMonth(d.getUTCMonth()-1,1);
 return d.toISOString().slice(0,10);
}
export function Reports({user}:{user:User}){
 const qc=useQueryClient(),isTeacher=user.role==='ADMIN_TEACHER',isParent=user.role==='PARENT';
 const [studentId,setStudentId]=useState(''),[period,setPeriod]=useState<'WEEK'|'MONTH'>('WEEK'),[start,setStart]=useState(defaultStart('WEEK'));
 const children=useQuery({queryKey:['children'],queryFn:async()=>(await api.get<Child[]>('/parent/children')).data,enabled:isParent});
 const students=useQuery({queryKey:['students'],queryFn:async()=>(await api.get<Student[]>('/admin/students')).data,enabled:isTeacher});
 const people=isTeacher?students.data:children.data;
 const selected=people?.find(p=>p.id===studentId)?.id||people?.[0]?.id;
 const path=isTeacher?`/admin/students/${selected}/reports`:isParent?`/parent/children/${selected}/reports`:'/student/reports';
 const reports=useQuery({queryKey:['reports',user.role,selected],queryFn:async()=>(await api.get<Report[]>(path)).data,enabled:!isTeacher&&!isParent||!!selected});
 const generate=useMutation({mutationFn:async()=>(await api.post<Report>(`/admin/students/${selected}/reports`,null,{params:{period,start}})).data,onSuccess:()=>qc.invalidateQueries({queryKey:['reports',user.role,selected]})});
 const [exporting,setExporting]=useState<string|null>(null),[exportError,setExportError]=useState<unknown>();
 async function downloadReport(report:Report,format:'PDF'|'XLSX'){
  const key=`${report.id}-${format}`;setExporting(key);setExportError(undefined);
  const url=isTeacher?`/admin/students/${selected}/reports/${report.id}/export`:isParent?`/parent/children/${selected}/reports/${report.id}/export`:`/student/reports/${report.id}/export`;
  try{
   const response=await api.get<Blob>(url,{params:{format},responseType:'blob'});
   const objectUrl=URL.createObjectURL(response.data);
   const anchor=document.createElement('a');anchor.href=objectUrl;anchor.download=`edutrack-report-${report.start}.${format.toLowerCase()}`;
   document.body.appendChild(anchor);anchor.click();anchor.remove();window.setTimeout(()=>URL.revokeObjectURL(objectUrl),1000);
  }catch(error){setExportError(error)}finally{setExporting(null)}
 }
 return <>{title('BÁO CÁO','Báo cáo học tập','Tổng hợp theo tuần hoặc tháng từ bài đã giao và kết quả được công bố.')}
 {(isTeacher||isParent)&&<div className="panel"><label>Học sinh<select value={selected||''} disabled={!people?.length} onChange={e=>setStudentId(e.target.value)}>{people?.map(p=><option key={p.id} value={p.id}>{p.fullName}</option>)}</select></label></div>}
 {isTeacher&&selected&&<div className="panel"><h2>Tạo báo cáo kỳ đã kết thúc</h2><form className="inline-form" onSubmit={e=>{e.preventDefault();generate.mutate()}}><select aria-label="Kỳ báo cáo" value={period} onChange={e=>{const p=e.target.value as 'WEEK'|'MONTH';setPeriod(p);setStart(defaultStart(p))}}><option value="WEEK">Theo tuần</option><option value="MONTH">Theo tháng</option></select><input aria-label="Ngày bắt đầu kỳ" type="date" value={start} onChange={e=>setStart(e.target.value)} required/><button className="button primary" disabled={generate.isPending}>Tổng hợp báo cáo</button></form><p className="muted">Tuần bắt đầu vào thứ Hai; tháng bắt đầu ngày 1. Chỉ kỳ đã kết thúc mới được tổng hợp.</p>{generate.isError&&<State text="" error={generate.error}/>} {generate.isSuccess&&<div className="success">Đã tổng hợp báo cáo.</div>}</div>}
 {students.isError||children.isError?<State text="" error={students.error||children.error}/>:isTeacher&&students.isPending||isParent&&children.isPending?<State text="Đang tải học sinh…"/>:!selected&&(isTeacher||isParent)?<State text="Chưa có học sinh đủ quyền xem báo cáo."/>:reports.isPending?<State text="Đang tải báo cáo…"/>:reports.isError?<State text="" error={reports.error}/>:!reports.data?.length?<State text="Chưa có báo cáo học tập. Giáo viên có thể tổng hợp một kỳ đã kết thúc."/>:<div className="question-list">{reports.data.map(r=><article key={r.id} className="panel"><div className="section-header"><h2>{r.period==='WEEK'?'Báo cáo tuần':'Báo cáo tháng'} · {new Date(`${r.start}T00:00:00Z`).toLocaleDateString('vi-VN')}</h2><span className="badge blue">{new Date(r.generatedAt).toLocaleDateString('vi-VN')}</span></div><p className="muted">{r.start} đến trước {r.end}</p><div className="analytics-facts"><p><strong>{r.completedCount}/{r.assignedCount}</strong><span>Bài hoàn thành · {pct(r.completionRate)}</span></p><p><strong>{pct(r.averagePercent)}</strong><span>Điểm trung bình ({r.publishedResultCount} kết quả)</span></p><p><strong>{r.progressPoints==null?'Chưa có dữ liệu':`${Number(r.progressPoints).toFixed(1)} điểm`}</strong><span>Thay đổi so với kỳ trước</span></p></div>{r.topics.length>0&&<div className="topic-list">{r.topics.map(t=><div key={t.name}><strong>{t.name}</strong><span>{pct(t.percentage)} · {t.questionCount} câu</span></div>)}</div>}<div className="actions"><button className="button secondary" type="button" disabled={!!exporting} onClick={()=>downloadReport(r,'PDF')}>{exporting===`${r.id}-PDF`?'Đang xuất…':'Tải PDF'}</button><button className="button secondary" type="button" disabled={!!exporting} onClick={()=>downloadReport(r,'XLSX')}>{exporting===`${r.id}-XLSX`?'Đang xuất…':'Tải Excel'}</button></div></article>)}</div>}
 {exportError&&<State text="" error={exportError}/>}
 </>;
}

export function Notifications({user}:{user:User}){
 const qc=useQueryClient(),[page,setPage]=useState(0),[recipientId,setRecipientId]=useState(''),[noticeTitle,setNoticeTitle]=useState(''),[body,setBody]=useState('');
 const students=useQuery({queryKey:['students'],queryFn:async()=>(await api.get<Student[]>('/admin/students')).data,enabled:user.role==='ADMIN_TEACHER'});
 const query=useQuery({queryKey:['notifications',page],queryFn:async()=>(await api.get<NoticePage>('/notifications',{params:{page,size:20}})).data});
 const count=useQuery({queryKey:['notifications-unread'],queryFn:async()=>(await api.get<{count:number}>('/notifications/unread-count')).data.count});
 const refresh=()=>{qc.invalidateQueries({queryKey:['notifications']});qc.invalidateQueries({queryKey:['notifications-unread']})};
 const read=useMutation({mutationFn:(id:string)=>api.post(`/notifications/${id}/read`),onSuccess:refresh});
 const readAll=useMutation({mutationFn:()=>api.post('/notifications/read-all'),onSuccess:refresh});
 const create=useMutation({mutationFn:()=>api.post('/admin/notifications',{recipientId:recipientId||students.data?.[0]?.id,type:'GENERAL',title:noticeTitle.trim(),body:body.trim()}),onSuccess:()=>{setNoticeTitle('');setBody('');refresh()}});
 return <>{title('CẬP NHẬT','Thông báo',`${count.data??'…'} thông báo chưa đọc`)}
 {user.role==='ADMIN_TEACHER'&&<div className="panel"><h2>Gửi thông báo cho học sinh</h2>{students.isPending?<State text="Đang tải danh sách học sinh…"/>:students.isError?<State text="" error={students.error}/>:!students.data?.length?<State text="Chưa có học sinh để gửi thông báo."/>:<form className="form-grid" onSubmit={e=>{e.preventDefault();if(recipientId||students.data?.[0])create.mutate()}}><label>Học sinh<select value={recipientId||students.data[0].id} onChange={e=>setRecipientId(e.target.value)}>{students.data.map(s=><option key={s.id} value={s.id}>{s.fullName} · {s.email}</option>)}</select></label><label>Tiêu đề<input value={noticeTitle} onChange={e=>setNoticeTitle(e.target.value)} required maxLength={180}/></label><label className="full">Nội dung<textarea value={body} onChange={e=>setBody(e.target.value)} required maxLength={2000}/></label><div className="modal-actions"><button className="button primary" disabled={create.isPending||!noticeTitle.trim()||!body.trim()} type="submit">Gửi thông báo</button></div></form>}{create.isError&&<State text="" error={create.error}/>} {create.isSuccess&&<div className="success">Đã tạo thông báo.</div>}</div>}
 <div className="panel"><div className="section-header"><h2>Hộp thông báo</h2><button className="button secondary" disabled={!count.data||readAll.isPending} onClick={()=>readAll.mutate()}>Đánh dấu tất cả đã đọc</button></div>
 {query.isPending?<State text="Đang tải thông báo…"/>:query.isError?<State text="" error={query.error}/>:query.data.content.length===0?<State text="Chưa có thông báo."/>:<div className="question-list">{query.data.content.map(n=><article className="question-item" key={n.id}><div><span className={`badge ${n.readAt?'gray':'blue'}`}>{n.type}</span><h3>{n.title}</h3><p>{n.body}</p><small>{new Date(n.createdAt).toLocaleString('vi-VN')}</small></div>{!n.readAt&&<button className="link-button" disabled={read.isPending} onClick={()=>read.mutate(n.id)}>Đánh dấu đã đọc</button>}</article>)}</div>}
 {read.isError&&<State text="" error={read.error}/>} {readAll.isError&&<State text="" error={readAll.error}/>}<div className="modal-actions"><button className="button ghost" disabled={page===0} onClick={()=>setPage(p=>p-1)}>Trang trước</button><span>Trang {page+1} / {Math.max(1,query.data?.totalPages||1)}</span><button className="button ghost" disabled={!query.data||page+1>=query.data.totalPages} onClick={()=>setPage(p=>p+1)}>Trang sau</button></div></div></>;
}
