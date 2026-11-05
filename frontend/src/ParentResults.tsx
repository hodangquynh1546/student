import {useQuery} from '@tanstack/react-query';
import {Link,useParams} from 'react-router-dom';
import {api,errorMessage} from './api';
import {AttachmentGallery} from './AttachmentGallery';

type Summary={attemptId:string;examId:string;examTitle:string;kind:string;score:number;totalPoints:number;submittedAt:string;publishedAt:string};
type ResultPage={items:Summary[];total:number;page:number;size:number};
type QuestionScore={questionId:string;type:string;body:string;maxPoints:number;score:number|null;teacherComment:string|null};
type Detail={result:Summary;questions:QuestionScore[]};

function State({message}:{message:string}){return <div className="panel"><p>{message}</p></div>}
function Score({score,total}:{score:number|null;total:number}){return <strong>{score==null?'Chưa chấm':`${score} / ${total} điểm`}</strong>}

export function ParentResults(){
 const {studentId}=useParams();
 const query=useQuery({queryKey:['parent-results',studentId],queryFn:async()=>(await api.get<ResultPage>(`/parent/children/${studentId}/results`)).data,enabled:!!studentId});
 return <><div className="page-head"><div><span className="eyebrow">KẾT QUẢ HỌC TẬP</span><h1>Điểm đã công bố</h1><p>Chỉ hiển thị kết quả của con đã liên kết và được giáo viên công bố.</p></div><Link className="button ghost" to="/children">Quay lại</Link></div>
 {query.isPending?<State message="Đang tải kết quả…"/>:query.isError?<State message={errorMessage(query.error)}/>:query.data.items.length===0?<State message="Chưa có kết quả được công bố."/>:<div className="panel"><div className="table-wrap"><table><thead><tr><th>Bài kiểm tra</th><th>Điểm</th><th>Ngày công bố</th><th></th></tr></thead><tbody>{query.data.items.map(item=><tr key={item.attemptId}><td><strong>{item.examTitle}</strong><small className="subline">{item.kind}</small></td><td><Score score={item.score} total={item.totalPoints}/></td><td>{new Date(item.publishedAt).toLocaleDateString('vi-VN')}</td><td><Link className="text-action" to={`/children/${studentId}/results/${item.attemptId}`}>Chi tiết</Link></td></tr>)}</tbody></table></div></div>}</>;
}

export function ParentResultDetail(){
 const {studentId,attemptId}=useParams();
 const query=useQuery({queryKey:['parent-result',studentId,attemptId],queryFn:async()=>(await api.get<Detail>(`/parent/children/${studentId}/results/${attemptId}`)).data,enabled:!!studentId&&!!attemptId});
 return <><div className="page-head"><div><span className="eyebrow">KẾT QUẢ ĐÃ CÔNG BỐ</span><h1>{query.data?.result.examTitle||'Chi tiết bài làm'}</h1><p>{query.data?<><Score score={query.data.result.score} total={query.data.result.totalPoints}/></>:'Điểm và nhận xét theo từng câu'}</p></div><Link className="button ghost" to={`/children/${studentId}/results`}>Quay lại</Link></div>
 {query.isPending?<State message="Đang tải chi tiết…"/>:query.isError?<State message={errorMessage(query.error)}/>:<div className="question-list">{query.data.questions.map((item,index)=><article className="panel question-item" key={item.questionId}><div><span className="eyebrow">CÂU {index+1} · {item.type}</span><h3>{item.body}</h3>{item.teacherComment&&<p>Nhận xét: {item.teacherComment}</p>}{item.type==='ESSAY'&&<AttachmentGallery listPath={`/parent/children/${studentId}/results/${attemptId}/questions/${item.questionId}/attachments`}/>}</div><Score score={item.score} total={item.maxPoints}/></article>)}</div>}</>;
}
