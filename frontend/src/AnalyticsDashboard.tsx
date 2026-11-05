import {useState} from 'react';
import {Link} from 'react-router-dom';
import {useQuery} from '@tanstack/react-query';
import {ResponsiveContainer,LineChart,Line,XAxis,YAxis,Tooltip,CartesianGrid} from 'recharts';
import {ArrowRight,BookOpen,CheckCircle2,ClipboardList,School,Users} from 'lucide-react';
import {api,errorMessage} from './api';
import type {Child,User} from './types';

type ScorePoint={attemptId:string;examTitle:string;publishedAt:string;score:number;totalPoints:number;percentage:number};
type Topic={topic:string;questionCount:number;percentage:number|null};
type Learner={studentId:string;assignedExams:number;completedExams:number;pendingExams:number;completionRate:number|null;publishedResults:number;averagePercentage:number|null;highestPercentage:number|null;lowestPercentage:number|null;scoreTrend:ScorePoint[];topics:Topic[]};
type Admin={totalStudents:number;totalClassrooms:number;totalExams:number;publishedExams:number;pendingGradingAttempts:number;upcomingExams:number;assignedStudentExamPairs:number;completedStudentExamPairs:number;completionRate:number|null;averagePublishedPercentage:number|null};

function Stat({label,value,icon:Icon}:{label:string;value:string|number;icon:typeof Users}){
 return <div className="metric-card"><div className="metric-icon"><Icon size={22}/></div><strong>{value}</strong><span>{label}</span></div>;
}
function percentage(value:number|null){return value==null?'Chưa có dữ liệu':`${value.toFixed(1)}%`}
function Loading(){return <div className="center-state">Đang tải thống kê…</div>}
function ErrorState({error}:{error:unknown}){return <div role="alert" className="alert">{errorMessage(error)}</div>}

function AdminView(){
 const query=useQuery({queryKey:['admin-dashboard'],queryFn:async()=>(await api.get<Admin>('/admin/analytics/dashboard')).data});
 return <>{query.isPending?<Loading/>:query.isError?<ErrorState error={query.error}/>:<><div className="overview-grid analytics-metrics"><Stat label="Học sinh" value={query.data.totalStudents} icon={Users}/><Stat label="Lớp học" value={query.data.totalClassrooms} icon={School}/><Stat label="Đề kiểm tra" value={query.data.totalExams} icon={ClipboardList}/><Stat label="Bài chờ chấm" value={query.data.pendingGradingAttempts} icon={BookOpen}/></div><div className="panel analytics-panel"><h2>Tiến độ chung</h2><div className="analytics-facts"><p><strong>{percentage(query.data.completionRate)}</strong><span>Tỷ lệ hoàn thành bài đã giao ({query.data.completedStudentExamPairs}/{query.data.assignedStudentExamPairs})</span></p><p><strong>{percentage(query.data.averagePublishedPercentage)}</strong><span>Điểm trung bình các kết quả đã công bố</span></p><p><strong>{query.data.upcomingExams}</strong><span>Đề sắp mở trong 7 ngày</span></p></div><Link className="text-action" to="/grading">Mở danh sách chấm điểm <ArrowRight size={16}/></Link></div></>}</>;
}

function LearnerView({studentId,role}:{studentId?:string;role:'STUDENT'|'PARENT'}){
 const path=role==='STUDENT'?'/student/analytics/dashboard':`/parent/children/${studentId}/analytics/dashboard`;
 const query=useQuery({queryKey:['learner-dashboard',role,studentId],queryFn:async()=>(await api.get<Learner>(path)).data,enabled:role==='STUDENT'||!!studentId});
 if(query.isPending)return <Loading/>;
 if(query.isError)return <ErrorState error={query.error}/>;
 const data=query.data;
 const trend=[...data.scoreTrend].reverse().map(p=>({...p,label:new Date(p.publishedAt).toLocaleDateString('vi-VN')}));
 return <><div className="overview-grid analytics-metrics"><Stat label="Kết quả đã công bố" value={data.publishedResults} icon={CheckCircle2}/><Stat label="Điểm trung bình" value={percentage(data.averagePercentage)} icon={BookOpen}/>{role==='STUDENT'&&<><Stat label="Bài đã làm" value={data.completedExams} icon={ClipboardList}/><Stat label="Bài chưa làm" value={data.pendingExams} icon={School}/></>}</div><div className="analytics-grid"><div className="panel analytics-panel"><h2>Điểm theo thời gian</h2>{trend.length?<div className="analytics-chart"><ResponsiveContainer width="100%" height="100%"><LineChart data={trend}><CartesianGrid strokeDasharray="3 3"/><XAxis dataKey="label"/><YAxis domain={[0,100]}/><Tooltip formatter={(value)=>`${value}%`}/><Line type="monotone" dataKey="percentage" stroke="#4f46e5" strokeWidth={3} dot={{r:4}}/></LineChart></ResponsiveContainer></div>:<p className="muted">Chưa có điểm được công bố để vẽ biểu đồ.</p>}</div><div className="panel analytics-panel"><h2>Kiến thức theo chủ đề</h2>{data.topics.length?<div className="topic-list">{data.topics.map(topic=><div key={topic.topic}><strong>{topic.topic}</strong><span>{percentage(topic.percentage)} · {topic.questionCount} câu</span></div>)}</div>:<p className="muted">Chưa có đủ dữ liệu chủ đề.</p>}{role==='STUDENT'&&<p className="muted">Tỷ lệ hoàn thành bài được giao: {percentage(data.completionRate)}</p>}</div></div></>;
}

function ParentView(){
 const children=useQuery({queryKey:['children'],queryFn:async()=>(await api.get<Child[]>('/parent/children')).data});
 const[selected,setSelected]=useState<string>();
 if(children.isPending)return <Loading/>;
 if(children.isError)return <ErrorState error={children.error}/>;
 if(!children.data.length)return <div className="panel empty"><strong>Chưa có con được liên kết</strong><span>Xác nhận lời mời của giáo viên để xem tiến độ học tập.</span><Link className="text-action" to="/children">Xác nhận liên kết <ArrowRight size={16}/></Link></div>;
 const studentId=selected&&children.data.some(c=>c.id===selected)?selected:children.data[0].id;
 return <><div className="panel analytics-child"><label>Chọn con<select value={studentId} onChange={e=>setSelected(e.target.value)}>{children.data.map(child=><option key={child.id} value={child.id}>{child.fullName}</option>)}</select></label><Link className="text-action" to={`/children/${studentId}/results`}>Xem điểm từng bài <ArrowRight size={16}/></Link></div><LearnerView role="PARENT" studentId={studentId}/></>;
}

export function AnalyticsDashboard({user}:{user:User}){
 return <><div className="hero"><span className="eyebrow light">BẢNG ĐIỀU KHIỂN</span><h1>Chào {user.fullName} 👋</h1><p>{user.role==='ADMIN_TEACHER'?'Tổng quan lớp học và bài làm.':user.role==='STUDENT'?'Theo dõi tiến độ học tập của bạn.':'Theo dõi kết quả đã công bố của con.'}</p><div className="hero-shapes"><span/><span/><span/></div></div><div className="section-header"><h2>Thống kê học tập</h2><span>Dữ liệu thực tế từ hệ thống</span></div>{user.role==='ADMIN_TEACHER'?<AdminView/>:user.role==='STUDENT'?<LearnerView role="STUDENT"/>:<ParentView/>}</>;
}
