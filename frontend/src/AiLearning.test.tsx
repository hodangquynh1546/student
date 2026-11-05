import {afterEach,expect,it,vi} from 'vitest';
import {cleanup,fireEvent,render,screen,waitFor} from '@testing-library/react';
import {QueryClient,QueryClientProvider} from '@tanstack/react-query';
import {StudentAiAssistant,TeacherAiQuestionDrafts} from './AiLearning';
import {api} from './api';

vi.mock('./api',()=>({api:{post:vi.fn()},errorMessage:()=> 'Dịch vụ đang gặp lỗi'}));
afterEach(()=>{cleanup();vi.clearAllMocks()});
function view(component:React.ReactNode){return render(<QueryClientProvider client={new QueryClient({defaultOptions:{mutations:{retry:false}}})}>{component}</QueryClientProvider>)}

it('học sinh chỉ gửi câu hỏi qua API học tập và hiển thị phản hồi thật',async()=>{
 vi.mocked(api.post).mockResolvedValue({data:{answer:'Hãy quy đồng mẫu số trước.',model:'configured-model'}});
 view(<StudentAiAssistant/>);
 fireEvent.change(screen.getByLabelText('Câu hỏi của bạn'),{target:{value:'Vì sao phải quy đồng?'}});
 fireEvent.click(screen.getByRole('button',{name:'Gửi câu hỏi'}));
 await waitFor(()=>expect(api.post).toHaveBeenCalledWith('/ai/student/ask',{question:'Vì sao phải quy đồng?',attemptId:null,questionId:null}));
 expect(await screen.findByText('Hãy quy đồng mẫu số trước.')).toBeInTheDocument();
});

it('học sinh thấy thông báo tạm khóa khi đang làm bài kiểm tra',async()=>{
 vi.mocked(api.post).mockRejectedValue(Object.assign(new Error('Conflict'),{isAxiosError:true,response:{status:409}}));
 view(<StudentAiAssistant/>);
 fireEvent.change(screen.getByLabelText('Câu hỏi của bạn'),{target:{value:'Giải thích giúp tôi'}});
 fireEvent.click(screen.getByRole('button',{name:'Gửi câu hỏi'}));
 expect(await screen.findByRole('alert')).toHaveTextContent('Trợ lý AI tạm khóa khi bạn đang làm bài kiểm tra');
});

it('giáo viên duyệt bản nháp AI trước khi lưu thủ công vào ngân hàng',async()=>{
 vi.mocked(api.post).mockImplementation(async(url)=>{
  if(url==='/ai/admin/questions/generate')return {data:{model:'configured-model',drafts:[{type:'TRUE_FALSE',subject:'Toán',gradeLevel:'6',topic:'Phân số',difficulty:'MEDIUM',body:'1/2 = 2/4?',content:{},answerKey:{correct:true},explanation:'Nhân cả tử và mẫu với 2',rubric:'',defaultPoints:1}]}} as never;
  if(url==='/admin/questions')return {data:{id:'saved'}} as never;
  throw new Error(`Unexpected endpoint ${url}`);
 });
 view(<TeacherAiQuestionDrafts/>);
 fireEvent.change(screen.getByLabelText('Môn học'),{target:{value:'Toán'}});
 fireEvent.change(screen.getByLabelText('Chủ đề'),{target:{value:'Phân số'}});
 fireEvent.change(screen.getByLabelText('Khối lớp'),{target:{value:'6'}});
 fireEvent.click(screen.getByRole('button',{name:'Tạo bản nháp'}));
 expect(await screen.findByDisplayValue('1/2 = 2/4?')).toBeInTheDocument();
 expect(api.post).not.toHaveBeenCalledWith('/admin/questions',expect.anything());
 fireEvent.click(screen.getByRole('button',{name:'Lưu vào ngân hàng'}));
 await waitFor(()=>expect(api.post).toHaveBeenCalledWith('/admin/questions',expect.objectContaining({body:'1/2 = 2/4?',answerKey:{correct:true}})));
});
