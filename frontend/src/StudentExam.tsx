import {useEffect,useRef,useState} from 'react';
import {Link,useNavigate,useParams} from 'react-router-dom';
import {useMutation,useQuery,useQueryClient} from '@tanstack/react-query';
import {AlertCircle,ArrowRight,BookOpen,CheckCircle2,Clock3,LoaderCircle} from 'lucide-react';
import {api,errorMessage} from './api';
import {AttachmentGallery} from './AttachmentGallery';

type ExamSummary={id:string;title:string;kind:string;startsAt:string|null;endsAt:string|null;dueAt:string|null;maxAttempts:number;attemptsUsed:number;attemptStatus:string|null;openAttemptId:string|null};
type ExamPage={items:ExamSummary[];total:number;page:number;size:number};
type Item={id:string;text:string};
type Question={id:string;type:string;body:string;content:{options?:Item[];left?:Item[];right?:Item[];items?:Item[];blanks?:Item[];template?:string};points:number};
type Saved={questionId:string;answer:Record<string,unknown>;markedForReview:boolean;savedAt:string};
type Attempt={id:string;examId:string;status:string;startedAt:string;deadlineAt:string|null;serverTime:string;questions:Question[];answers:Saved[]};
type AnswerState={answer:Record<string,unknown>;markedForReview:boolean};
type Submission={status:string;submittedAt:string};
type Result={score:number|null;totalPoints:number;status:string};

function date(value:string|null){return value?new Date(value).toLocaleString('vi-VN'):'Không giới hạn'}
function remaining(ms:number){const n=Math.max(0,Math.ceil(ms/1000));return `${Math.floor(n/3600).toString().padStart(2,'0')}:${Math.floor(n%3600/60).toString().padStart(2,'0')}:${(n%60).toString().padStart(2,'0')}`}
function statusText(status:string|null){return ({IN_PROGRESS:'Đang làm',SUBMITTED:'Đã nộp',EXPIRED:'Hết giờ',PENDING_GRADING:'Chờ chấm',GRADED:'Đã chấm'} as Record<string,string>)[status||'']||'Chưa làm'}
function ErrorBox({error}:{error:unknown}){return <div className="alert" role="alert"><AlertCircle size={17}/>{errorMessage(error)}</div>}

export function StudentExamList(){
  const nav=useNavigate(),qc=useQueryClient();
  const query=useQuery({queryKey:['student-exams'],queryFn:async()=> (await api.get<ExamPage>('/student/exams')).data});
  const start=useMutation({mutationFn:async(id:string)=>(await api.post<Attempt>(`/student/exams/${id}/attempts`)).data,onSuccess:attempt=>{qc.invalidateQueries({queryKey:['student-exams']});nav(`/attempts/${attempt.id}`)}});
  const now=Date.now();
  return <><div className="page-head"><div><span className="eyebrow">BÀI TẬP</span><h1>Bài được giao</h1><p>Bắt đầu, tiếp tục và theo dõi bài làm của bạn.</p></div></div>
    {query.isPending?<div className="center-state"><LoaderCircle className="spin"/>Đang tải bài được giao…</div>:query.isError?<ErrorBox error={query.error}/>:query.data.items.length===0?<div className="panel empty"><BookOpen size={32}/><strong>Chưa có bài được giao</strong><span>Bài mới sẽ xuất hiện khi giáo viên giao cho bạn.</span></div>:<div className="card-grid">{query.data.items.map(exam=>{
      const opens=(!exam.startsAt||new Date(exam.startsAt).getTime()<=now)&&(!exam.endsAt||new Date(exam.endsAt).getTime()>now);
      const canStart=opens&&exam.attemptsUsed<exam.maxAttempts&&!exam.openAttemptId;
      return <article className="class-card" key={exam.id}><span className="badge blue">{statusText(exam.attemptStatus)}</span><h3>{exam.title}</h3><p>{exam.kind} · Hạn nộp: {date(exam.dueAt)}</p><p>Đã làm {exam.attemptsUsed}/{exam.maxAttempts} lượt</p><div className="class-card-bottom">{exam.openAttemptId?<Link className="button primary" to={`/attempts/${exam.openAttemptId}`}>Tiếp tục <ArrowRight size={16}/></Link>:canStart?<button className="button primary" disabled={start.isPending} onClick={()=>start.mutate(exam.id)}>Bắt đầu <ArrowRight size={16}/></button>:<span>{opens?'Đã hết lượt':'Chưa mở hoặc đã đóng'}</span>}</div></article>;
    })}</div>}{start.isError&&<ErrorBox error={start.error}/>}</>;
}

function QuestionInput({question,value,onChange,disabled}:{question:Question;value:Record<string,unknown>;onChange:(next:Record<string,unknown>)=>void;disabled:boolean}){
  const c=question.content;
  switch(question.type){
    case 'SINGLE_CHOICE':return <div className="answer-options">{(c.options||[]).map(o=><label className="answer-option" key={o.id}><input type="radio" name={question.id} checked={value.selectedId===o.id} disabled={disabled} onChange={()=>onChange({selectedId:o.id})}/>{o.text}</label>)}</div>;
    case 'MULTIPLE_CHOICE':return <div className="answer-options">{(c.options||[]).map(o=>{const ids=Array.isArray(value.selectedIds)?value.selectedIds as string[]:[];return <label className="answer-option" key={o.id}><input type="checkbox" checked={ids.includes(o.id)} disabled={disabled} onChange={()=>onChange({selectedIds:ids.includes(o.id)?ids.filter(id=>id!==o.id):[...ids,o.id]})}/>{o.text}</label>})}</div>;
    case 'TRUE_FALSE':return <div className="answer-options">{[true,false].map(v=><label className="answer-option" key={String(v)}><input type="radio" name={question.id} checked={value.value===v} disabled={disabled} onChange={()=>onChange({value:v})}/>{v?'Đúng':'Sai'}</label>)}</div>;
    case 'SHORT_ANSWER':return <label>Câu trả lời<input disabled={disabled} value={String(value.text??'')} onChange={e=>onChange({text:e.target.value})}/></label>;
    case 'ESSAY':return <label>Bài làm<textarea disabled={disabled} rows={9} value={String(value.text??'')} onChange={e=>onChange({text:e.target.value})}/></label>;
    case 'MATCHING':return <div className="answer-pairs">{(c.left||[]).map(left=><label key={left.id}>{left.text}<select disabled={disabled} value={(value.pairs as Record<string,string>|undefined)?.[left.id]||''} onChange={e=>onChange({pairs:{...(value.pairs as object||{}),[left.id]:e.target.value}})}><option value="">Chọn đáp án ghép</option>{(c.right||[]).map(right=><option key={right.id} value={right.id}>{right.text}</option>)}</select></label>)}</div>;
    case 'ORDERING':{const ids=Array.isArray(value.order)?value.order as string[]:(c.items||[]).map(item=>item.id);return <div className="answer-pairs"><p>Sắp xếp bằng nút lên và xuống.</p>{ids.map((itemId,i)=>{const item=(c.items||[]).find(x=>x.id===itemId);return <div className="order-row" key={itemId}><strong>{i+1}. {item?.text||itemId}</strong><div><button type="button" className="button ghost" disabled={disabled||i===0} onClick={()=>{const next=[...ids];[next[i-1],next[i]]=[next[i],next[i-1]];onChange({order:next})}}>Lên</button><button type="button" className="button ghost" disabled={disabled||i===ids.length-1} onClick={()=>{const next=[...ids];[next[i+1],next[i]]=[next[i],next[i+1]];onChange({order:next})}}>Xuống</button></div></div>})}</div>}
    case 'FILL_IN_THE_BLANK':return <div className="answer-pairs">{c.template&&<p>{c.template}</p>}{(c.blanks||[]).map((blank,i)=><label key={blank.id}>Ô {i+1}{blank.text?` – ${blank.text}`:''}<input disabled={disabled} value={(value.blanks as Record<string,string>|undefined)?.[blank.id]||''} onChange={e=>onChange({blanks:{...(value.blanks as object||{}),[blank.id]:e.target.value}})}/></label>)}</div>;
    default:return <p>Loại câu hỏi này chưa được hỗ trợ trên giao diện.</p>;
  }
}

export function StudentAttempt(){
  const{id}=useParams(),nav=useNavigate(),qc=useQueryClient();
  const query=useQuery({queryKey:['student-attempt',id],queryFn:async()=> (await api.get<Attempt>(`/student/attempts/${id}`)).data,refetchInterval:30000});
  const [index,setIndex]=useState(0),[values,setValues]=useState<Record<string,AnswerState>>({}),[seconds,setSeconds]=useState(Date.now()),[error,setError]=useState<unknown>(null),[saving,setSaving]=useState(false),[submitting,setSubmitting]=useState(false);
  const initialized=useRef<string|null>(null),dirty=useRef<Record<string,AnswerState>>({}),chain=useRef<Promise<void>>(Promise.resolve()),timer=useRef<ReturnType<typeof setTimeout>|null>(null),clockOffset=useRef(0);
  useEffect(()=>{if(query.data){clockOffset.current=new Date(query.data.serverTime).getTime()-Date.now();if(initialized.current!==query.data.id){initialized.current=query.data.id;setValues(Object.fromEntries(query.data.answers.map(a=>[a.questionId,{answer:a.answer,markedForReview:a.markedForReview}])))} }},[query.data]);
  useEffect(()=>{const tick=setInterval(()=>setSeconds(Date.now()),1000);return()=>clearInterval(tick)},[]);
  useEffect(()=>()=>{if(timer.current)clearTimeout(timer.current)},[]);
  async function flush(){
    if(timer.current)clearTimeout(timer.current);
    chain.current=chain.current.catch(()=>{}).then(async()=>{
      setSaving(true);
      try{while(Object.keys(dirty.current).length){const [questionId,state]=Object.entries(dirty.current)[0];delete dirty.current[questionId];try{await api.put(`/student/attempts/${id}/answers/${questionId}`,state)}catch(e){if(!dirty.current[questionId])dirty.current[questionId]=state;throw e}}setError(null)}finally{setSaving(false)}
    });
    return chain.current.catch(e=>{setError(e);throw e});
  }
  function update(questionId:string,next:AnswerState){setValues(old=>({...old,[questionId]:next}));dirty.current[questionId]=next;if(timer.current)clearTimeout(timer.current);timer.current=setTimeout(()=>{void flush().catch(()=>{})},700)}
  async function move(next:number){try{await flush();setIndex(next)}catch{/* Keep the answer visible until it saves. */}}
  async function submit(){if(!query.data||!confirm('Nộp bài? Sau khi nộp, bạn không thể sửa đáp án.'))return;setSubmitting(true);try{await flush();const keyName=`edutrack-submit-${id}`;let submitKey=sessionStorage.getItem(keyName);if(!submitKey){submitKey=crypto.randomUUID();sessionStorage.setItem(keyName,submitKey)}await api.post<Submission>(`/student/attempts/${id}/submit`,{submitKey});await qc.invalidateQueries({queryKey:['student-attempt',id]});await qc.invalidateQueries({queryKey:['student-exams']});sessionStorage.removeItem(keyName);setError(null)}catch(e){setError(e)}finally{setSubmitting(false)}}
  if(query.isPending)return <div className="center-state"><LoaderCircle className="spin"/>Đang tải bài làm…</div>;
  if(query.isError)return <ErrorBox error={query.error}/>;
  const attempt=query.data,question=attempt.questions[index],editable=attempt.status==='IN_PROGRESS';
  const left=attempt.deadlineAt?new Date(attempt.deadlineAt).getTime()-seconds-clockOffset.current:null;
  const state=question?values[question.id]||{answer:{},markedForReview:false}:null;
  return <><div className="page-head"><div><span className="eyebrow">BÀI LÀM</span><h1>{editable?'Làm bài kiểm tra':'Bài đã kết thúc'}</h1><p>Trạng thái: {statusText(attempt.status)}</p></div><Link className="button ghost" to="/exams">Danh sách bài</Link></div>
    <div className="exam-toolbar"><span><Clock3 size={18}/> {left===null?'Không giới hạn thời gian':remaining(left)}</span><span>{saving?'Đang lưu…':error?'Lưu chưa thành công':'Đáp án được lưu tự động'}</span></div>
    {error&&<ErrorBox error={error}/ >}
    {!editable?<div className="panel"><CheckCircle2 color="#238459"/><h2>{attempt.status==='EXPIRED'?'Đã hết thời gian làm bài':'Đã nộp bài'}</h2><p>{attempt.status==='PENDING_GRADING'?'Bài tự luận đang chờ chấm điểm.':'Kết quả sẽ hiển thị khi giáo viên cho phép.'}</p><ResultPanel id={id!}/><button className="button secondary" onClick={()=>nav('/exams')}>Quay lại danh sách</button></div>:<div className="exam-layout"><div className="panel"><div className="section-header"><h2>Câu {index+1}/{attempt.questions.length}</h2><span>{question?.points} điểm</span></div>{question&&state&&<><p className="exam-question">{question.body}</p><QuestionInput question={question} value={state.answer} onChange={answer=>update(question.id,{...state,answer})} disabled={left!==null&&left<=0}/>{question.type==='ESSAY'&&<AttachmentGallery listPath={`/student/attempts/${id}/questions/${question.id}/attachments`} uploadPath={`/student/attempts/${id}/questions/${question.id}/attachments`} disabled={left!==null&&left<=0}/>}<label className="review-toggle"><input type="checkbox" checked={state.markedForReview} onChange={e=>update(question.id,{...state,markedForReview:e.target.checked})}/>Đánh dấu xem lại</label></>}
      <div className="exam-actions"><button className="button ghost" disabled={index===0||saving} onClick={()=>move(index-1)}>Câu trước</button><button className="button secondary" disabled={saving} onClick={()=>void flush().catch(()=>{})}>Lưu ngay</button><button className="button ghost" disabled={index===attempt.questions.length-1||saving} onClick={()=>move(index+1)}>Câu tiếp</button></div></div><aside className="panel"><h2>Danh sách câu hỏi</h2><div className="question-jump">{attempt.questions.map((q,i)=><button key={q.id} className={`${i===index?'current ':''}${values[q.id]?.markedForReview?'review ':''}${Object.keys(values[q.id]?.answer||{}).length?'answered':''}`} onClick={()=>move(i)}>{i+1}</button>)}</div><button className="button primary full-width" disabled={submitting||saving||left!==null&&left<=0} onClick={submit}>{submitting?'Đang nộp…':'Nộp bài'}</button></aside></div>}
  </>;
}

function ResultPanel({id}:{id:string}){const result=useQuery({queryKey:['student-result',id],queryFn:async()=> (await api.get<Result>(`/student/attempts/${id}/result`)).data,retry:false});if(result.isSuccess&&result.data.score!==null)return <p><strong>Điểm: {result.data.score}/{result.data.totalPoints}</strong></p>;return null}
