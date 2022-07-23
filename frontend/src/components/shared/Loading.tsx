import {
  Spinner,
  Overlay,
} from '@blueprintjs/core';

import Row from 'react-bootstrap/Row';
import Col from 'react-bootstrap/Col';

function Loading(props: {loading: boolean}) {
    return <Overlay isOpen={props.loading} {...props}>
    <Row style={{display: 'flex', alignItems: 'center', height: '100%', width: '100%'}}>
      <Col xs={12} style={{justifyContent: 'center'}}>
        <Spinner intent="primary"/>
      </Col>
    </Row>
  </Overlay>
}

export {
  Loading
}
