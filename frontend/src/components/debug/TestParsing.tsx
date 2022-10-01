// import React from 'react';

// import { connect } from 'react-redux';

// import { Formik } from 'formik';
// import * as Yup from 'yup';

// import {
//   Button,
//   Card,
//   InputGroup,
//   HTMLSelect,
//   MenuItem,
//   Menu,
//   H3,
//   Popover,
// } from '@blueprintjs/core';

// import peg from 'pegjs';

// import Container from 'react-bootstrap/Container';
// import Row from 'react-bootstrap/Row';
// import Col from 'react-bootstrap/Col';

// import { checkPayloads, getPayload } from 'parsers/PayloadMap';
// import { createParser } from 'lib/grammar';
// import _ from 'lodash';

// import { mapStateToProps, mapDispatchToProps, mergeProps, initialRefresh } from 'store';


// class TestParsingComponentBase extends React.Component {

//   state = {
//     loading: false,
//     //
//     currentLanguage: null,
//     currentParser: 'default',
//     //
//     results: null,
//     payload: null,
//   };

//   currentLanguage = () => {
//     return (this.state.currentLanguage || this.props.languages[0]);
//   }

//   currentParser = () => {
//     return (this.state.currentParser || this.currentParsers()[0]);
//   }

//   currentParsers = () => {
//     return Object.keys((this.props.grammars || {})[this.currentLanguage()] || {});
//   }

//   selectLanguage = (currentLanguage) => {
//     this.setState({
//       currentLanguage,
//     });
//   }

//   selectParser = (currentParser) => {
//     this.setState({
//       currentParser,
//     });
//   }

//   parse = (text) => {
//     const currentLanguage = this.currentLanguage();
//     const currentParser = this.currentParser();

//     if (this.props.grammars && currentLanguage && currentParser) {
//       const parser = this.props.grammars[currentLanguage][currentParser];

//       if (parser) {
//         const results = parser(text);

//         const payload = getPayload(currentLanguage)(results, null, {}, []);

//         this.setState({
//           results,
//           payload,
//         });
//         // ugh
//         return null;
//       }
//     }

//     this.setState({
//       results: {
//         error: "Invalid state",
//         currentLanguage, 
//         currentParser,
//       },
//       payload: null,
//     });
//   }

//   render() {
//     console.warn(this.props.languages);
//     return <React.Fragment>
//       {
//         //query
//       }
//       <Row style={{marginTop: 20}}>
//         <Col xs={12}>
//           <Card>
//             <div style={{display: 'flex'}}>
//               <div>
//                 <HTMLSelect
//                   onChange={(e) => (this.selectLanguage(e.target.value))}
//                   value={this.state.currentLanguage}
//                 >
//                   {this.props.languages.map((l) => (<option value={l}>{l}</option>))}
//                 </HTMLSelect>
//               </div>
//               <div>
//                 <HTMLSelect
//                   onChange={(e) => (this.selectParser(e.target.value))}
//                   value={this.state.currentParser}
//                 >
//                   {this.currentParsers().map((p) => (<option value={p}>{p}</option>))}
//                 </HTMLSelect>
//               </div>
//             </div>
//             <Formik
//               initialValues={{name: ""}}
//               onSubmit={(values, { setSubmitting }) => {
//                 this.parse(values.name)
//               }}
//               validationSchema={Yup.object().shape({
//                 name: Yup.string()
//                   .min(1)
//                   .required('Required'),
//               })}
//             >
//               {props => {
//                 const {
//                   values,
//                   touched,
//                   errors,
//                   dirty,
//                   handleChange,
//                   handleSubmit,
//                   setFieldValue,
//                 } = props;
//                 return <Container style={{marginTop: 20}}>
//                   <form>
//                     <InputGroup
//                       id="name" 
//                       value={values.name}
//                       onChange={handleChange}
//                       placeholder="Query name" 
//                       rightElement={<Button icon="floppy-disk" intent="primary" type="submit" onClick={handleSubmit}/>} 
//                     />
//                   </form>
//                 </Container>
//               }}
//             </Formik>
//           </Card>
//         </Col>
//       </Row>
//       {
//         // results
//       }
//       <Row style={{marginTop: 20}}>
//         <Col xs={12}>
//           { this.state.results && <Card>
//             <H3>Raw Grammar</H3>
//             <pre>{JSON.stringify(this.state.results, null, 2)}</pre>
//           </Card> }
//         </Col>
//       </Row>
//       {
//         // payload
//       }
//       <Row style={{marginTop: 20}}>
//         <Col xs={12}>
//           { this.state.payload && <Card>
//             <pre>{
//               //JSON.stringify(this.state.payload, null, 2)
//               }</pre>
//             <div>
//               <H3>Display</H3>
//               <Menu style={{width: 400}}>
//                 <MenuItem 
//                   style={{fontFamily: 'monospace'}}
//                   text={((this.state.payload || {}).display || {}).text}
//                   labelElement={
//                     <div>
//                       <i>{((this.state.payload || {}).display || {}).ident}</i>
//                       <Popover interactionKind="hover" position="right-top">
//                         <Button icon="help" small={true} minimal={true} />
//                         <Card style={{padding: 10}}>
//                           {((this.state.payload || {}).display || {}).description}
//                         </Card>
//                       </Popover>
//                     </div>
//                   }
//                 />
//               </Menu>
//             </div>
//             <div>
//               <H3>Nodes</H3>
//               <pre>{JSON.stringify((this.state.payload || {}).nodes, null, 2)}</pre>
//             </div>
//             <div>
//               <H3>Edges</H3>
//               <pre>{JSON.stringify((this.state.payload || {}).edges, null, 2)}</pre>
//             </div>
//           </Card> }
//         </Col>
//       </Row>      
//     </React.Fragment>

//   }
// }

// const TestParsingComponent = connect(
//   mapStateToProps,
//   mapDispatchToProps,
//   mergeProps,
// )(TestParsingComponentBase);

// export {
//   TestParsingComponent
// };

export function Blah() {
  
}